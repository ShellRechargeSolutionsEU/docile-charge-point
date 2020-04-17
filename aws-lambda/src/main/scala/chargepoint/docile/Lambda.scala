package chargepoint.docile

import java.net.URI

import chargepoint.docile.test.{Loader, ResultSummary, RunOnce, Runner, RunnerConfig}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.thenewmotion.ocpp.{Version, VersionFamily}
import VersionFamily.V1X
import chargepoint.docile.dsl.AwaitTimeoutInMillis
import javax.net.ssl.SSLContext

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class Reporter(var s: Seq[Any] = Seq[Any]()) {
  def print(x:Any):Unit = s = s.+:(x)
  def report: String = s.reverse.mkString("\n")
}

object Lambda extends App {
  lazy val reporter = Reporter()

  lazy val s3Client = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain).build()

  lazy val cpId = sys.env("cpId")
  lazy val cpUri = new URI(sys.env("cpUri"))
  lazy val cpVersion = sys.env("cpVersion")
  lazy val cpAuthKey = sys.env.get("cpAuthKey")
  lazy val bucketName = sys.env.get("s3Bucket")

  case class Script(name:String, content:Array[Byte])

  def getScriptFromS3(event: S3Event):Option[Script] =
    event.getRecords.asScala.headOption.map(r => {
      val key = r.getS3.getObject.getKey
      val bucket = r.getS3.getBucket.getName
      val s3Object = s3Client.getObject(new GetObjectRequest(bucket, key))
      val objectData = s3Object.getObjectContent
      val content = Stream.continually(objectData.read).takeWhile(_ != -1).map(_.toByte).toArray
      println(s"found a script called $key in $bucket")
      Script(key, content)
    })

  def writeReportToS3(name: String, report:String) = {
    bucketName.map(n => s3Client.putObject(n, name+".report", report))
  }

  def executeScript(script:Script): Option[String] = {
    val runnerCfg = RunnerConfig(
      number = 1,
      chargePointId = cpId,
      uri = cpUri,
      ocppVersion = Version.withName(cpVersion).getOrElse(Version.V15),
      authKey = cpAuthKey,
      repeat = RunOnce,
      defaultAwaitTimeout = AwaitTimeoutInMillis(45 * 1000),
      sslContext = SSLContext.getDefault
    )

    println(s"executing ${script.name} as $cpId on $cpUri")
    val runner: Runner[V1X.type] = Loader.runnerFor(V1X, script.name, script.content)

    Try(runner.run(runnerCfg)) match {
      case Success(testsPassed) =>
        val succeeded = ResultSummary.summarizeResults(testsPassed, reporter.print)
        writeReportToS3(script.name, reporter.report)
        sys.exit(if (succeeded) 0 else 1)
      case Failure(e) =>
        System.err.println(s"Could not run tests: ${e.getMessage}")
        e.printStackTrace()
        sys.exit(2)
    }
  }

  def trigger(event: S3Event, context:Context) = {
    println("someone dropped some scala")
    getScriptFromS3(event).flatMap(executeScript)
  }
}
