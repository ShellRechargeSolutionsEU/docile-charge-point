package chargepoint.docile
package test

import java.io.File
import java.net.URI

import scala.tools.reflect.ToolBox
import scala.util.{Failure, Success, Try}
import scala.collection.mutable
import scala.concurrent.{Await, Future, Promise, duration}
import duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import chargepoint.docile.dsl._
import com.thenewmotion.ocpp
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

case class RunnerConfig(
  number: Int,
  chargePointId: String,
  uri: URI,
  ocppVersion: ocpp.Version,
  authKey: Option[String],
  repeat: RepeatMode
)

/**
  * A runner runs a sequence of given test cases according to a certain configuration
  *
  * @param testCases The test cases to run
  */
class Runner(testCases: Seq[TestCase]) {

  private val logger: Logger = Logger(LoggerFactory.getLogger("runner"))

  /**
    * Run the test cases in this runner according to the given configuration
    *
    * It returns a structure containing the result of every test case in every run for every simulated charge point.
    * The structure is like this:
    *
    * {{{
    *  Map[String,                           Seq[                          Map[String, TestResult]]]
    *   ^ multiple simulated charge points    ^ multiple consecutive runs   ^ multiple test cases
    * }}}
    *
    * @param runnerCfg
    */
  def run(runnerCfg: RunnerConfig): Map[String, Seq[Map[String, TestResult]]] =
  // TODO create special type to return these things in a clearer way?
  // or better yet, make it take a function that can fold the results of
  // consecutive runs into an arbitrary result type. That should help with heap
  // memory if someone wants to run it overnight for some reason.
    if (runnerCfg.number > 1)
      runMultipleCases(runnerCfg, runnerCfg.number)
    else
      Map(runnerCfg.chargePointId -> runOneCase(runnerCfg))

  def runOneCase(runnerCfg: RunnerConfig): Seq[Map[String, TestResult]] =
    runnerCfg.repeat match {
      case RunOnce                 => List(runOnce(testCases, runnerCfg))
      case repeatMode: RunRepeated => runRepeat(testCases, runnerCfg, repeatMode)
    }

  def runMultipleCases(runnerCfg: RunnerConfig, n: Int): Map[String, Seq[Map[String, TestResult]]] = {
    val results = List.fill(n)(Promise[(String, Seq[Map[String, TestResult]])]())

    val testThreads = 1.to(n) map { i =>
      val runnerConfig = runnerCfg.copy(chargePointId = runnerCfg.chargePointId.format(i))

      new Thread {
        override def run(): Unit = {
          val resultsForChargePoint = runnerConfig.chargePointId -> Runner.this.runOneCase(runnerConfig)
          results(i - 1).success(resultsForChargePoint)
        }
      }
    }

    testThreads.foreach(_.start())
    logger.debug("Multi run threads started")
    testThreads.foreach(_.join(0))
    logger.debug("Multi run threads joined")

    Await.result(Future.sequence(results.map(_.future)), Duration.Inf).toMap
  }

  private def runRepeat(testCases: Seq[TestCase], runnerCfg: RunnerConfig, repeatMode: RunRepeated): Seq[Map[String, TestResult]] = {
    val (shouldIStop, msg): ((Int, Boolean) => Boolean, String) = repeatMode match {
      case Repeat(numberOfTimes, _) =>
        ((n, _) => n >= numberOfTimes,
         s"Running test case $numberOfTimes times"
        )
      case UntilSuccess(_) =>
        ((_, isSuccess) => isSuccess,
         "Running test case until it succeeds"
        )
      case Indefinitely(_) =>
        ((_, _) => System.in.available() > 0,
         "Running in indefinite repeat mode. Press <ENTER> to stop."
        )
    }

    val res = mutable.ArrayBuffer.empty[Map[String, TestResult]]

    logger.info(msg)

    var i = 0

    while (!shouldIStop(i, res.lastOption.exists(_.values.forall(_ == TestPassed)))) {
      res += runOnce(testCases, runnerCfg)
      Thread.sleep(repeatMode.pause)

      i += 1
    }

    res
  }

  private def runOnce(testCases: Seq[TestCase], runnerCfg: RunnerConfig): Map[String, TestResult] =
    testCases.map(testCase => runCase(runnerCfg, testCase)).toMap

  private def runCase(runnerCfg: RunnerConfig, c: TestCase): (String, TestResult) = {
    logger.info(s"Going to run ${c.name}")

    val res = Try(c.test().runConnected(
      new ReceivedMsgManager(),
      runnerCfg.chargePointId,
      runnerCfg.uri,
      runnerCfg.ocppVersion,
      runnerCfg.authKey
    )) match {
      case Success(_)                => TestPassed
      case Failure(e: ScriptFailure) => TestFailed(e)
      case Failure(e: Exception)     => TestFailed(ExecutionError(e))
      case Failure(e)                => throw e
    }

    logger.debug(s"Test ${c.name} run; disconnecting...")
    logger.debug(s"Disconnected OCPP connection for ${runnerCfg.chargePointId}/${c.name}")

    c.name -> res
  }
}


object Runner {

  private val logger = LoggerFactory.getLogger("runner")

  def interactive: Runner = new Runner(
    Seq(TestCase("Interactive test", () => new InteractiveOcppTest))
  )

  def forFiles(files: Seq[String]): Runner =
    new Runner(files.map(loadFile))

  private def loadFile(f: String): TestCase = {

    val file = new File(f)
    val testNameRegex = "(?:.*/)?([^/]+?)(?:\\.[^.]*)?$".r
    val testName = f match {
      case testNameRegex(n) => n
      case _                => f
    }

    import reflect.runtime.currentMirror
    val toolbox = currentMirror.mkToolBox()

    val preamble = s"""
                   |import com.thenewmotion.ocpp.messages._
                   |
                   |import scala.language.postfixOps
                   |import scala.concurrent.duration._
                   |import java.time._
                   |import com.typesafe.scalalogging.Logger
                   |import org.slf4j.LoggerFactory
                   |
                   |import chargepoint.docile.dsl.AwaitTimeout
                   |
                   |new chargepoint.docile.dsl.OcppTest
                   |  with chargepoint.docile.dsl.CoreOps
                   |  with chargepoint.docile.dsl.expectations.Ops
                   |  with chargepoint.docile.dsl.shortsend.Ops {
                   |
                   |  private implicit val awaitTimeout: AwaitTimeout = AwaitTimeout(45.seconds)
                   |
                   |  def run() {
                   """.stripMargin
    val appendix = ";\n  }\n}"

    val fileContents = scala.io.Source.fromFile(file).getLines.mkString("\n")

    logger.info(s"Parsing and compiling script '$f'")

    val fileAst = toolbox.parse(preamble + fileContents + appendix)

    logger.info(s"Parsed '$f'")

    val compiledCode = toolbox.compile(fileAst)

    logger.info(s"Compiled '$f'")

    TestCase(testName, () => compiledCode().asInstanceOf[OcppTest])
  }
}

