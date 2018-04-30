package chargepoint.docile

import java.net.URI

import ch.qos.logback.classic.{Level, Logger}

import scala.util.{Failure, Success, Try}
import chargepoint.docile.dsl.{ExecutionError, ExpectationFailed}
import com.thenewmotion.ocpp.Version
import com.typesafe.scalalogging.StrictLogging
import org.rogach.scallop._
import org.slf4j.LoggerFactory
import test._

import scala.concurrent.ExecutionContextExecutor

object Main extends App with StrictLogging {

  object conf extends ScallopConf(args) {
    implicit val versionConverter =
      singleArgConverter(
        Version.withName(_).get, {
          case _: NoSuchElementException => Left("Invalid OCPP version provided")
        }
      )

    val version = opt[Version](
      default = Some(Version.V16),
      descr = "OCPP version"
    )

    val authKey = opt[String](
      descr = "Authorization key to use for Basic Auth (hex-encoded, 40 characters)"
    )

    val chargePointId = opt[String](
      default = Some("03000001"),
      descr = "ChargePointIdentity to identify ourselves to the Central System"
    )

    val forever = toggle(
      default = Some(false),
      descrYes = "Keep executing script until terminated"
    )

    val interactive = toggle(
      default = Some(false),
      descrYes = "Start REPL to enter and run a test interactively"
    )

    val numberInParallel = opt[Int](
      default = Some(1),
      descr = "Start given number of instances of the script at the same time (can be combined with --repeat)"
    )

    val repeat = opt[Int](
      default = Some(1),
      descr = "Repeat execution of the scripts this number of times"
    )

    val repeatPause = opt[Int](
      default = Some(1000),
      descr = "Number of milliseconds to wait between repeat runs"
    )

    val untilSuccess = toggle(
      default = Some(false),
      descrYes = "Keep executing scripts until they all succeed"
    )

    val verbose = opt[Int](
      default = Some(3),
      descr = "Verbosity (0-5)"
    )

    val uri = trailArg[URI](
      descr = "URI of the Central System"
    )

    val files = trailArg[List[String]](
      required = false,
      descr = "files with test cases to load"
    )

    def makesSense: Either[String, Unit] = {
      val repeatModesSpecified =
        List(
          conf.forever(),
          conf.repeat.toOption.exists(_ > 1),
          conf.untilSuccess()
        ).filter(identity).size

      val senseChecks = List(
        "Tssk, grapjas" ->
          conf.numberInParallel.toOption.exists(_ < 1),
        "You can't combine -i and -n, sorry" ->
          (conf.interactive() && conf.numberInParallel() > 1),
        "You can only specify one of --forever, --repeat and --until-success" ->
          (repeatModesSpecified > 1),
        "You have to give files on the command-line for a non-interactive run" ->
          (!conf.interactive() && !conf.files.toOption.exists(_.nonEmpty))
      )

      senseChecks.filter(_._2).headOption.map(_._1).toLeft(())
    }

    verify()
  }

  val rootLogger = LoggerFactory.getLogger("ROOT").asInstanceOf[Logger]

  val rootLogLevel = conf.verbose() match {
    case 0 => Level.OFF
    case 1 => Level.ERROR
    case 2 => Level.WARN
    case 3 => Level.INFO
    case 4 => Level.DEBUG
    case 5 => Level.TRACE
    case _ => sys.error("Invalid verbosity, should be 0, 1, 2, 3, 4 or 5")
  }

  rootLogger.setLevel(rootLogLevel)

  implicit val ec: ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global

  conf.makesSense.left.foreach { errMsg =>
    println(errMsg)
    sys.exit(1)
  }

  val repeatMode =
    if (conf.repeat() > 1)
      Repeat(conf.repeat(), conf.repeatPause())
    else if (conf.untilSuccess())
      UntilSuccess(conf.repeatPause())
    else if (conf.forever())
      Indefinitely(conf.repeatPause())
    else RunOnce

  val runnerCfg = RunnerConfig(
    number = conf.numberInParallel(),
    chargePointId = conf.chargePointId(),
    uri = conf.uri(),
    ocppVersion = conf.version(),
    authKey = conf.authKey.toOption,
    repeat = repeatMode
  )

  val runner: Runner =
    if (conf.interactive())
      Runner.interactive
    else
      Runner.forFiles(conf.files())

  Try(runner.run(runnerCfg)) match {
    case Success(testsPassed) =>
      val succeeded = summarizeResults(testsPassed)
      sys.exit(if (succeeded) 0 else 1)
    case Failure(e) =>
      System.err.println(s"Could not run tests: ${e.getMessage}")
      e.printStackTrace()
      sys.exit(2)
  }

  private def summarizeResults(testResults: Map[String, Seq[Map[String, TestResult]]]): Boolean = {

    // we do result formatting differently depending on whether we're doing a
    // single run (one charge point, one pass through the test script), or if
    // we're doing a complex one (multiple charge point and/or multiple repeats)
    val isSingleRun = testResults.size == 1 && testResults.toSeq.headOption.exists(_._2.size == 1)
    if (isSingleRun) {
      val singleRunResult =
        testResults
          .headOption
          .flatMap(_._2.headOption)
          .getOrElse(Map.empty[String, TestResult])

      summarizeSingleRun(singleRunResult)
    } else {
      summarizeComplexRun(testResults)
    }
  }

  private def summarizeSingleRun(testResults: Map[String, TestResult]): Boolean = {
    val outcomes = testResults map  { case (testName, outcome) =>

      val outcomeDescription = outcome match {
        case TestFailed(ExpectationFailed(msg)) =>
          s"❌  $msg"
        case TestFailed(ExecutionError(e)) =>
          s"💥  ${e.getClass.getSimpleName} ${e.getMessage}\n" +
          s"\t${e.getStackTrace.mkString("\n\t")}"
        case TestPassed =>
          s"✅"
      }

      println(s"$testName: $outcomeDescription")

      outcome
    }

    outcomes.collect({ case TestFailed(_) => }).isEmpty
  }

  private def summarizeComplexRun(testResults: Map[String, Seq[Map[String, TestResult]]]): Boolean = {
    val countsPerChargePoint: Map[String, (Int, Int, Int)] = testResults.mapValues { runs =>
      runs.foldLeft((0, 0, 0)) { case (counts, results) =>
          val countsForRun = results.values.foldLeft((0,0,0)) {
            case ((f, e, p), TestFailed(ExpectationFailed(_))) => (f+1, e  , p)
            case ((f, e, p), TestFailed(ExecutionError(_)))    => (f  , e+1, p)
            case ((f, e, p), TestPassed)                       => (f  , e  , p+1)
          }

        (counts._1 + countsForRun._1, counts._2 + countsForRun._2, counts._3 + countsForRun._3)
      }
    }

    countsPerChargePoint foreach { case (chargePointId, counts) =>
      println(s"$chargePointId: ${counts._1} failed / ${counts._2} errors / ${counts._3} passed")
    }

    !countsPerChargePoint.values.exists(c => c._1 != 0 || c._2 != 0)
  }
}
