package chargepoint.docile.dsl

import java.net.URI

import com.thenewmotion.ocpp.Version.V20
import com.thenewmotion.ocpp.VersionFamily.{CsMessageTypesForVersionFamily, CsmsMessageTypesForVersionFamily}
import javax.net.ssl.SSLContext

import scala.language.higherKinds
import scala.concurrent.ExecutionContext
import com.thenewmotion.ocpp.{Version, Version1X, VersionFamily}
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20.{CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes}

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

