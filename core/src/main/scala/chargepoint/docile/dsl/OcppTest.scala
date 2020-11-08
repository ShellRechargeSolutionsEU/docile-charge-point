package chargepoint.docile.dsl

import java.net.URI

import com.thenewmotion.ocpp.Version.V20
import com.thenewmotion.ocpp.VersionFamily.{CsMessageTypesForVersionFamily, CsmsMessageTypesForVersionFamily}
import javax.net.ssl.SSLContext

import scala.language.higherKinds
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.DurationInt
import com.thenewmotion.ocpp.{Version, Version1X, VersionFamily}
import com.thenewmotion.ocpp.json.api._
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait OcppTest[VFam <: VersionFamily] {

  // these type variables are filled in differently for OCPP 1.X and OCPP 2.0
  type OutgoingReqBound <: Request
  type IncomingResBound <: Response
  type OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _]
  type IncomingReqBound <: Request
  type OutgoingResBound <: Response
  type IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _]
  type VersionBound <: Version

  implicit val csMessageTypes: CsMessageTypesForVersionFamily[VFam, IncomingReqBound, OutgoingResBound, IncomingReqRes]
  implicit val csmsMessageTypes: CsmsMessageTypesForVersionFamily[VFam, OutgoingReqBound, IncomingResBound, OutgoingReqRes]

  val executionContext: ExecutionContext

  /**
    * The current OCPP with some associated data
    *
    * This member is effectively the interface between CoreOps and the mutable
    * data required to implement the core operations defined in CoreOps
    */
  protected val connection: DocileConnection[
    VFam,
    VersionBound,
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]

  def runConnected(
    chargePointId: String,
    endpoint: URI,
    version: VersionBound,
    authKey: Option[String],
    defaultAwaitTimeout: AwaitTimeout
  )(implicit sslContext: SSLContext): Unit = {
    connection.connect(chargePointId, endpoint, version, authKey)(executionContext, sslContext)
    run(defaultAwaitTimeout)
    connection.disconnect()
  }


  protected def run(defaultAwaitTimeout: AwaitTimeout): Unit
}

trait Ocpp1XTest extends OcppTest[VersionFamily.V1X.type] {

  type OutgoingReqBound = CentralSystemReq
  type IncomingResBound = CentralSystemRes
  type OutgoingReqRes[Q <: CentralSystemReq, S <: CentralSystemRes] = CentralSystemReqRes[Q, S]
  type IncomingReqBound = ChargePointReq
  type OutgoingResBound = ChargePointRes
  type IncomingReqRes[Q <: ChargePointReq, S <: ChargePointRes] = ChargePointReqRes[Q, S]
  type VersionBound = Version1X

  override protected val connection: DocileConnection[
    VersionFamily.V1X.type,
    Version1X,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ] = DocileConnection.forVersion1x()

}

object Ocpp1XTest {

  trait V1XOps extends CoreOps[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
    ] with expectations.Ops[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
    ] with shortsend.OpsV1X
}

trait Ocpp20Test extends OcppTest[VersionFamily.V20.type] {

  override type OutgoingReqBound = CsmsRequest
  override type IncomingResBound = CsmsResponse
  override type OutgoingReqRes[Q <: CsmsRequest, S <: CsmsResponse] = CsmsReqRes[Q, S]
  override type IncomingReqBound = CsRequest
  override type OutgoingResBound = CsResponse
  override type IncomingReqRes[Q <: CsRequest, S <: CsResponse] = CsReqRes[Q, S]
  override type VersionBound = V20.type

  override protected val connection: DocileConnection[
    VersionFamily.V20.type,
    Version.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ] = DocileConnection.forVersion20()
}

object Ocpp20Test {

  trait V20Ops extends CoreOps[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with expectations.Ops[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with shortsend.OpsV20
    with ocpp20transactions.Ops
}

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

          receivedMsgManager.enqueue(
            GenericIncomingMessage[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](req, respond _)
          )

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