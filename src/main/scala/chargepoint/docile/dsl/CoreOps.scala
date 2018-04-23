package chargepoint.docile
package dsl

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import com.thenewmotion.ocpp.messages.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes}
import expectations.IncomingMessage

trait CoreOps extends OpsLogging with MessageLogging {

  protected def connectionData: OcppConnectionData

  /**
   * Send an OCPP request to the Central System under test.
   *
   * This method works asynchronously. That means the method call returns immediately, and does not return the response.
   *
   * To get the response, await the incoming message using the awaitIncoming method defined below, or use the
   * synchronous methods from the "shortsend" package.
   *
   * @param req
   * @param reqRes
   * @tparam Q
   */
  def send[Q <: CentralSystemReq](req: Q)(implicit reqRes: CentralSystemReqRes[Q, _ <: CentralSystemRes]): Unit =
    connectionData.ocppClient match {
      case None =>
        throw ExpectationFailed("Trying to send an OCPP message while not connected")
      case Some (client) =>
        outgoingLogger.info(s"$req")
        client.send(req)(reqRes) onComplete {
          case Success(res) =>
            incomingLogger.info(s"$res")
            connectionData.receivedMsgManager.enqueue(
              IncomingMessage(res)
            )
          case Failure(e) =>
            opsLogger.error(s"Failed to get response to outgoing OCPP request $req: ${e.getMessage}")
            // TODO handle this nicer; should be possible to write scripts expecting failure (without using catch ;-) )
            throw ExecutionError(e)
    }
  }

  def awaitIncoming(num: Int): Seq[IncomingMessage] = {
    def getMsgs = connectionData.receivedMsgManager.dequeue(num)
    Try(Await.result(getMsgs, 45.seconds)) match {
      case Success(msgs)                => msgs
      case Failure(e: TimeoutException) => fail(s"Expected message not received after 45 seconds")
      case Failure(e)                   => error(e)
    }
  }

  /**
   * Throw away all incoming messages that have not yet been awaited.
   *
   * This can be used in interactive mode to get out of a situation where you've received a bunch of messages that you
   * don't really care about, and you want to get on with things.
   */
  def flushQ(): Unit = connectionData.receivedMsgManager.flush()

  def fail(message: String): Nothing = throw ExpectationFailed(message)

  def error(throwable: Throwable): Nothing = throw ExecutionError(throwable)

  def wait(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  def prompt(cue: String): String = {
    println(s"$cue: ")
    scala.io.StdIn.readLine()
  }
}
