package chargepoint.docile
package dsl

import java.util.concurrent.TimeoutException

import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.VersionFamily.{CsMessageTypesForVersionFamily, CsmsMessageTypesForVersionFamily}

import scala.language.higherKinds
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import com.thenewmotion.ocpp.json.api.{OcppError, OcppException}
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait CoreOps[
  VFam <: VersionFamily,
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
] extends OpsLogging with MessageLogging {

  type IncomingMessage =
    GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]
  type IncomingMessageProcessor[+T] =
    GenericIncomingMessageProcessor[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes, T]

  implicit val csmsMessageTypes: CsmsMessageTypesForVersionFamily[VFam, OutReq, InRes, OutReqRes]
  implicit val csMessageTypes:     CsMessageTypesForVersionFamily[VFam, InReq, OutRes, InReqRes]

  implicit def executionContext: ExecutionContext

  object IncomingMessage {
    def apply(res: InRes): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](res)
    def apply(req: InReq, respond: OutRes => Unit): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](req, respond)
    def apply(error: OcppError): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](error)
  }

  val logger = Logger(LoggerFactory.getLogger("script"))
  def say(m: String): Unit = logger.info(m)

  protected def connection: DocileConnection[VFam, _, OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]

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
  def send[Q <: OutReq](req: Q)(implicit reqRes: OutReqRes[Q, _ <: InRes]): Unit =
    connection.sendRequestAndManageResponse(req)

  // WIP an operation to add a default handler for a certain subset of incoming messages
  def handlingIncomingMessages[T](proc: IncomingMessageProcessor[_])(f: => T): T = {
    connection.pushIncomingMessageHandler(proc)
    val result = f
    connection.popIncomingMessageHandler()

    result
  }

  def awaitIncoming(num: Int)(implicit awaitTimeout: AwaitTimeout): Seq[IncomingMessage] = {

    val timeout = awaitTimeout.toDuration
    def getMsgs = connection.receivedMsgManager.dequeue(num)

    Try(Await.result(getMsgs, timeout)) match {
      case Success(msgs)                => msgs
      case Failure(e: TimeoutException) => fail(s"Expected message not received after $timeout")
      case Failure(e)                   => error(e)
    }
  }

  /**
   * Throw away all incoming messages that have not yet been awaited.
   *
   * This can be used in interactive mode to get out of a situation where you've received a bunch of messages that you
   * don't really care about, and you want to get on with things.
   */
  def flushQ(): Unit = connection.receivedMsgManager.flush()

  def fail(message: String): Nothing = throw ExpectationFailed(message)

  def error(throwable: Throwable): Nothing = throw ExecutionError(throwable)

  def sleep(duration: Duration): Unit = {
    opsLogger.info(s"Sleeping for $duration")
    Thread.sleep(duration.toMillis)
  }

  def prompt(cue: String): String = {
    println(s"$cue: ")
    scala.io.StdIn.readLine()
  }
}
