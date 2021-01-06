package chargepoint.docile.dsl

import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}

import scala.language.higherKinds

/**
 * A partial function with side effects working on incoming OCPP messages
 */
trait GenericIncomingMessageProcessor[
  OutgoingReqBound <: Request,
  IncomingResBound <: Response,
  OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _],
  IncomingReqBound <: Request,
  OutgoingResBound <: Response,
  IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _],
  +T
] {
  type IncomingMessage = GenericIncomingMessage[
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]

  /** Whether this processor can do something with a certain incoming message */
  def accepts(msg: IncomingMessage): Boolean

  /**
   * The outcome of applying this processor to the given incoming message.
   *
   *  Applying the processor do a message outside of its domain should throw
   *  a MatchError.
   */
  def result(msg: IncomingMessage): T

  /**
   * Execute the side effects of this processor.
   *
   * In an OCPP test, this is supposed to happen when an assertion expecting a
   * certain incoming message has received an incoming message that matches
   * the expectation.
   */
  def fireSideEffects(msg: IncomingMessage): Unit

  def lift(msg: IncomingMessage): Option[T] =
    if (accepts(msg))
      Some(result(msg))
    else
      None
}

