package chargepoint.docile.dsl

import java.net.URI

import com.thenewmotion.ocpp.{Version, Version1X, VersionFamily}
import com.thenewmotion.ocpp.json.api.{Ocpp1XJsonClient, Ocpp20JsonClient, OcppException, OcppJsonClient, RequestHandler}
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20.{CsReqRes, CsRequest, CsResponse, CsmsReqRes, CsmsRequest, CsmsResponse}
import com.typesafe.scalalogging.Logger
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait DocileConnection[
  VFam <: VersionFamily,
  VersionBound <: Version, // shouldn't be necessary. we need some type level function from versionfamily to this
  OutgoingReqBound <: Request,
  IncomingResBound <: Response,
  OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _],
  IncomingReqBound <: Request,
  OutgoingResBound <: Response,
  IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _]
] extends MessageLogging {

  val receivedMsgManager: ReceivedMsgManager[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes] =
    new ReceivedMsgManager[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes]()

  var incomingMessageHandlerStack: List[GenericIncomingMessageProcessor[
    OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes, _
  ]] = List()

  var ocppClient: Option[OcppJsonClient[
    VFam,
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]] = None

  // TODO handle this more gently. The identity should be known after the script is started, regardless of whether the connection was established or not.
  // that also has to do with being able to renew connections or disconnect and reconnect while executing a script
  def chargePointIdentity: String = connectedCpId.getOrElse(sys.error("Asked for charge point ID on not yet connected DocileConnection"))

  var connectedCpId: Option[String] = None

  private val connectionLogger = Logger(LoggerFactory.getLogger("connection"))

  final def pushIncomingMessageHandler(handler: GenericIncomingMessageProcessor[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes, _]): Unit = {
    incomingMessageHandlerStack = handler :: incomingMessageHandlerStack
  }

  final def popIncomingMessageHandler(): Unit = {
    incomingMessageHandlerStack = incomingMessageHandlerStack.tail
  }

  def connect(chargePointId: String,
              endpoint: URI,
              version: VersionBound,
              authKey: Option[String]
             )(implicit executionContext: ExecutionContext, sslContext: SSLContext): Unit = {

    connectedCpId = Some(chargePointId)

    connectionLogger.info(s"Connecting to OCPP v${version.name} endpoint $endpoint")

    val connection = createClient(chargePointId, endpoint, version, authKey)(executionContext, sslContext) {
      new RequestHandler[IncomingReqBound, OutgoingResBound, IncomingReqRes] {
        def apply[REQ <: IncomingReqBound, RES <: OutgoingResBound](req: REQ)(implicit reqRes: IncomingReqRes[REQ, RES], ec: ExecutionContext): Future[RES] = {

          incomingLogger.info(s"$req")

          val responsePromise = Promise[OutgoingResBound]()

          def respond(res: OutgoingResBound): Unit = {
            outgoingLogger.info(s"$res")
            responsePromise.success(res)
            ()
          }

          val incomingMsg = GenericIncomingMessage[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](req, respond _)
          val incomingMsgHandler = incomingMessageHandlerStack.find(_.accepts(incomingMsg))

          incomingMsgHandler.map(_.fireSideEffects(incomingMsg)).getOrElse(receivedMsgManager.enqueue(incomingMsg))

          // TODO nicer conversion?
          responsePromise.future.map(_.asInstanceOf[RES])(ec)
        }
      }
    }

    connection.onClose.foreach { _ =>
      connectionLogger.info(s"Gracefully disconnected from endpoint $endpoint")
      ocppClient = None
    }(executionContext)

    ocppClient = Some(connection)
  }

  def sendRequestAndManageResponse[REQ <: OutgoingReqBound](req: REQ)(
    implicit reqRes: OutgoingReqRes[REQ, _ <: IncomingResBound],
    executionContext: ExecutionContext
  ): Unit =
    ocppClient match {
      case None =>
        throw ExpectationFailed("Trying to send an OCPP message while not connected")
      case Some(client) =>
        outgoingLogger.info(s"$req")
        client.send(req)(reqRes) onComplete {
          case Success(res) =>
            incomingLogger.info(s"$res")
            receivedMsgManager.enqueue(
              GenericIncomingMessage[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](res)
            )
          case Failure(OcppException(ocppError)) =>
            incomingLogger.info(s"$ocppError")
            receivedMsgManager.enqueue(
              GenericIncomingMessage[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](ocppError)
            )
          case Failure(e) =>
            connectionLogger.error(s"Failed to get response to outgoing OCPP request $req: ${e.getMessage}\n\t${e.getStackTrace.mkString("\n\t")}")
            throw ExecutionError(e)
        }
    }

  /** Template method to be implemented by version-specific extending classes to establish a connection for that
   * version of OCPP */
  protected def createClient(
                              chargePointId: String,
                              endpoint: URI,
                              version: VersionBound,
                              authKey: Option[String]
                            )(implicit executionContext: ExecutionContext, sslContext: SSLContext): RequestHandler[IncomingReqBound, OutgoingResBound, IncomingReqRes] => OcppJsonClient[
    VFam,
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]


  def disconnect(): Unit = ocppClient.foreach { conn =>
    Await.result(conn.close(), 45.seconds)
  }
}

object DocileConnection {
  def forVersion1x(): DocileConnection[
    VersionFamily.V1X.type,
    Version1X,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ] = {
    new DocileConnection[VersionFamily.V1X.type, Version1X, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes] {
      type VersionBound = Version1X

      override protected def createClient(chargePointId: String, endpoint: URI, version: Version1X, authKey: Option[String])(implicit executionContext: ExecutionContext, sslContext: SSLContext): RequestHandler[ChargePointReq, ChargePointRes, ChargePointReqRes] => Ocpp1XJsonClient = { reqHandler =>
        OcppJsonClient.forVersion1x(chargePointId, endpoint, List(version), authKey)(reqHandler)(executionContext, sslContext)
      }
    }
  }

  def forVersion20(): DocileConnection[
    VersionFamily.V20.type,
    Version.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ] = {
    new DocileConnection[VersionFamily.V20.type, Version.V20.type, CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes] {
      type VersionBound = Version.V20.type

      override protected def createClient(chargePointId: String, endpoint: URI, version: Version.V20.type, authKey: Option[String])(implicit executionContext: ExecutionContext, sslContext: SSLContext): RequestHandler[CsRequest, CsResponse, CsReqRes] => Ocpp20JsonClient = { reqHandler =>
        OcppJsonClient.forVersion20(chargePointId, endpoint, authKey)(reqHandler)(executionContext, sslContext)
      }
    }
  }
}
