package chargepoint.docile.dsl

import com.thenewmotion.ocpp.json.api.OcppError
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}

import scala.language.higherKinds

sealed trait GenericIncomingMessage[
  OutgoingReqBound <: Request,
  IncomingResBound <: Response,
  OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _],
  IncomingReqBound <: Request,
  OutgoingResBound <: Response,
  IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _]
]

object GenericIncomingMessage {
  def apply[
    OutReq <: Request,
    InRes <: Response,
    OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
    InReq <: Request,
    OutRes <: Response,
    InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
  ](res: InRes): IncomingResponse[
    OutReq,
    InRes,
    OutReqRes,
    InReq,
    OutRes,
    InReqRes
  ] = IncomingResponse[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](res)


  def apply[
    OutReq <: Request,
    InRes <: Response,
    OutReqRes[_ <: OutReq, _ <: InRes]  <: ReqRes[_, _],
    InReq <: Request,
    OutRes <: Response,
    InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
  ](
    req: InReq,
    respond: OutRes => Unit
  ): IncomingRequest[
    OutReq,
    InRes,
    OutReqRes,
    InReq,
    OutRes,
    InReqRes
  ] = IncomingRequest[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](req, respond)

  def apply[
    OutReq <: Request,
    InRes <: Response,
    OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
    InReq <: Request,
    OutRes <: Response,
    InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
  ](error: OcppError): IncomingError[
    OutReq,
    InRes,
    OutReqRes,
    InReq,
    OutRes,
    InReqRes
  ] = IncomingError[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](error)
}

// TODO writing 1.X / 2.0 cross-working code is pretty painful
// In library interface we could:
// make OcppJsonClient / OcppJsonServer take just 1 type parameter and arrange the rest with type members in the
// classes
// however, that would mean that the type of request taken by send() would not be exposed directly to the user, so
// you'd have to pass in yet another implicit argument to witness that the request you're sending is compatible with
// the client version
// so how common is the 1.x / 2.0 scenario really? Given how different the conceptual models in the versinos are,
// you'd probably have completely different code for handling or sending 1.x and 2.0 requests anyway. Unless, like
// docile-charge-point, you don't care about the meaning of the requests and responses and you're just being middleware
// passing them on. Which might actually be a pretty common scenario.
case class IncomingResponse[
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes]  <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
](
  res: InRes
) extends GenericIncomingMessage[
  OutReq,
  InRes,
  OutReqRes,
  InReq,
  OutRes,
  InReqRes
]

case class IncomingRequest[
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
](
  req: InReq,
  respond: OutRes => Unit
) extends GenericIncomingMessage[
  OutReq,
  InRes,
  OutReqRes,
  InReq,
  OutRes,
  InReqRes
]

case class IncomingError[
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
](error: OcppError) extends GenericIncomingMessage[
  OutReq,
  InRes,
  OutReqRes,
  InReq,
  OutRes,
  InReqRes
]
