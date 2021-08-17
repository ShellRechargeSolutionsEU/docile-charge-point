package chargepoint.docile
package dsl
package expectations

import com.thenewmotion.ocpp.VersionFamily

import scala.language.higherKinds
import com.thenewmotion.ocpp.messages.{Message, ReqRes, Request, Response}
import com.thenewmotion.ocpp.messages.v1x._
import com.thenewmotion.ocpp.json.PayloadErrorCode
import com.thenewmotion.ocpp.json.api.OcppError

trait Ops[
  VFam <: VersionFamily,
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
] {
  self: CoreOps[VFam, OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] =>

  // TODO document why we need this subset of IncomingMessageProcessor
  sealed trait IncomingRequestProcessor[+T] extends IncomingMessageProcessor[T]

  def expectIncoming[T](proc: IncomingMessageProcessor[T])(implicit awaitTimeout: AwaitTimeout): T = {
    val promisedMsg = awaitIncoming(1).head

    proc.lift(promisedMsg) match {
      case None =>
        self.fail(s"Expectation failed on $promisedMsg")
      case Some(t) =>
        proc.fireSideEffects(promisedMsg)
        t
    }
  }

  // TODO: HList time?
  def expectInAnyOrder[T](expectations: IncomingMessageProcessor[T]*)(implicit awaitTimeout: AwaitTimeout): Seq[T] = {
    val messages = awaitIncoming(expectations.length)

    val firstSatisfyingPermutation =
      expectations
        .permutations
        .find(_.zip(messages).forall({case (e, m) => e.accepts(m)}))

    firstSatisfyingPermutation match {
      case None =>
        self.fail(s"Expectation failed on $messages")
      case Some(exps) =>
        exps.zip(messages).map {
          case (e, m) =>
            e.fireSideEffects(m)
            e.result(m)
        }
    }
  }

  def expectAllIgnoringUnmatched[T](expectations: IncomingMessageProcessor[T]*)(implicit awaitTimeout: AwaitTimeout): Seq[T] = {

    def loop(matchesCount: Int, results: IndexedSeq[Option[T]]): IndexedSeq[Option[T]] = {
      if (matchesCount >= expectations.length) {
        results
      } else {
        val Seq(m) = awaitIncoming(1)
        val processorIndex = expectations.indexWhere(_.accepts(m))

        if (processorIndex < 0) {
          opsLogger.info(s"Ignoring message $m")
          loop(matchesCount, results)
        } else {
          val p = expectations(processorIndex)
          p.fireSideEffects(m)
          val result = p.result(m)
          val nextResults = results.updated(processorIndex, Some(result))
          val nextMatchesCount: Int = results(processorIndex) match {
            case Some(_) => matchesCount
            case None => matchesCount + 1
          }

          opsLogger.info(s"Received $processorIndex: $m, $nextMatchesCount to go")
          loop(nextMatchesCount, nextResults)
        }
      }
    }

    loop(0, IndexedSeq.fill(expectations.size)(None)).flatten
  }

  def anything: IncomingMessageProcessor[IncomingMessage] =
    new IncomingMessageProcessor[IncomingMessage] {
      def accepts(msg: IncomingMessage): Boolean = true
      def result(msg: IncomingMessage): IncomingMessage = msg
      def fireSideEffects(msg: IncomingMessage): Unit = {}
    }

  def matching[T](matchPF: PartialFunction[Message, T]): IncomingMessageProcessor[T] = {
      val incomingMessageMatcher: PartialFunction[IncomingMessage, Message] = {
        case msg: IncomingRequest[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] if matchPF.isDefinedAt(msg.req) => msg.req
        case msg: IncomingResponse[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] if matchPF.isDefinedAt(msg.res) => msg.res
      }

      anything restrictedBy incomingMessageMatcher restrictedBy matchPF
    }

  def requestMatching[T](
    requestMatch: PartialFunction[InReq, T]
  ): IncomingRequestProcessor[T] = new IncomingRequestProcessor[T] {
    def accepts(msg: IncomingMessage) = msg match {
      case msg: IncomingRequest[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] => requestMatch.isDefinedAt(msg.req)
      case _                                                                       => false
    }

    def result(msg: IncomingMessage): T = msg match {
      case msg: IncomingRequest[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] => requestMatch(msg.req)
      case _ => error(new RuntimeException(
          "IncomingRequestProcessor encountered non-request in result" +
          " method. The accepts method should have made this impossible."
        ))
    }

    def fireSideEffects(msg: IncomingMessage): Unit = ()
  }

  def error: IncomingMessageProcessor[OcppError] =
    anything restrictedBy { case msg: IncomingError[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] => msg.error }

  def errorWithCode(code: PayloadErrorCode): IncomingMessageProcessor[OcppError] =
    error restrictedBy { case e@OcppError(`code`, _) => e }


  def getConfigurationReq: IncomingRequestProcessor[GetConfigurationReq] = requestMatching { case r: GetConfigurationReq => r }
  def changeConfigurationReq = requestMatching { case r: ChangeConfigurationReq => r }
  def getDiagnosticsReq = requestMatching { case r: GetDiagnosticsReq => r }
  def changeAvailabilityReq = requestMatching { case r: ChangeAvailabilityReq => r }
  def getLocalListVersionReq = requestMatching { case r: GetLocalListVersionReq.type => r }
  def sendLocalListReq = requestMatching { case r: SendLocalListReq => r }
  def clearCacheReq: IncomingRequestProcessor[ClearCacheReq.type] = requestMatching { case r: ClearCacheReq.type => r }
  def resetReq = requestMatching { case r: ResetReq => r }
  def updateFirmwareReq = requestMatching { case r: UpdateFirmwareReq => r }
  def remoteStartTransactionReq = requestMatching { case r: RemoteStartTransactionReq => r }
  def remoteStopTransactionReq = requestMatching { case r: RemoteStopTransactionReq => r }
  def reserveNowReq = requestMatching { case r: ReserveNowReq => r }
  def cancelReservationReq = requestMatching { case r: CancelReservationReq => r }
  def unlockConnectorReq = requestMatching { case r: UnlockConnectorReq => r }


  implicit class RichIncomingMessageProcessor[T](self: IncomingMessageProcessor[T]) {
    def restrictedBy[U](restriction: PartialFunction[T, U]): IncomingMessageProcessor[U] =
      new IncomingMessageProcessor[U] {
        def accepts(msg: IncomingMessage) = self.accepts(msg) && restriction.isDefinedAt(self.result(msg))

        def result(msg: IncomingMessage) = restriction.apply(self.result(msg))

        def fireSideEffects(msg: IncomingMessage) = self.fireSideEffects(msg)
      }

    def withSideEffects(sideEffects: IncomingMessage => Unit): IncomingMessageProcessor[T] =
      new IncomingMessageProcessor[T] {
        def accepts(msg: IncomingMessage) = self.accepts(msg)

        def result(msg: IncomingMessage) = self.result(msg)

        def fireSideEffects(msg: IncomingMessage): Unit = {
          self.fireSideEffects(msg)
          sideEffects(msg)
        }
      }

    def printingTheMessage: IncomingMessageProcessor[T] =
      self withSideEffects println
  }

  implicit class RichIncomingRequestProcessor[T](self: IncomingRequestProcessor[T]) {
    def respondingWith(res: OutRes): IncomingRequestProcessor[T] = respondingWith(_ => res)

    def respondingWith(resBuilder: T => OutRes): IncomingRequestProcessor[T] = new IncomingRequestProcessor[T] {
      def accepts(msg: IncomingMessage): Boolean = self.accepts(msg)
      def result(msg: IncomingMessage): T = self.result(msg)
      def fireSideEffects(msg: IncomingMessage): Unit = {
        self.fireSideEffects(msg)
        msg match {
          case msg: IncomingRequest[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes] =>
            val matchResult = result(msg)
            msg.respond(resBuilder(matchResult))
          case x =>
            fail(
              "Expecation failed: expected request, " +
                s"received something else instead: $x"
            )
        }
      }
    }
  }

}
