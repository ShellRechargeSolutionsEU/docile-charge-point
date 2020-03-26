import java.time.ZonedDateTime
import chargepoint.docile.dsl._
import com.thenewmotion.ocpp.messages.v1x._
import scala.concurrent.duration._
import scala.util._

/**
 * Continuously listens to remote starts.
 * Upon start, changes to started state where it listens to remote stops for 30 minutes.
 * If there was no remote stop, it stops the current session.
 * Use with --forever parameter to create an always-on, virtual chargepoint.
 */

say("Waiting for remote start message")

val noTimeout: AwaitTimeout = AwaitTimeout(scala.concurrent.duration.Duration.Inf)
val startRequest = expectIncoming(remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)))(noTimeout)
val chargeTokenId = startRequest.idTag

say("Received remote start, authorizing...")
val auth = authorize(chargeTokenId).idTag

if (auth.status == AuthorizationStatus.Accepted) {
  say("Obtained authorization from Central System; starting transaction")
  Thread.sleep(2000) // to simulate real chargepoint behaviour
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))
  val transId = startTransaction(meterStart = 300, idTag = chargeTokenId).transactionId
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))

  say(s"Transaction started with ID $transId; awaiting remote stop")

  val stopTimeout = AwaitTimeout(FiniteDuration(30, "minutes"))
  def waitForValidRemoteStop(): Unit =
    Try(
      expectIncoming(
        requestMatching({
          case r: RemoteStopTransactionReq => r.transactionId == transId
        })
          .respondingWith(RemoteStopTransactionRes(_))
      )(stopTimeout)
    ) match {
      case Success(_) =>
        say("Received RemoteStopTransaction request; stopping transaction")
        ()
      case Failure(ExpectationFailed(exc)) if exc.startsWith("Expected message not received after") =>
        say(s"Received no RemoteStopTransaction within ${stopTimeout.timeout}; stopping transaction")
        ()
      case Failure(ExpectationFailed(_)) =>
        say(s"Received RemoteStopTransaction request for other transaction with ID. I'll keep waiting for a stop for $transId.")
        waitForValidRemoteStop()
    }


  waitForValidRemoteStop()
  Thread.sleep(1000) // to simulate real chargepoint behaviour
  send(StatusNotificationReq(ChargePointScope, status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)), None, None))
  send(StopTransactionReq(transactionId = transId, idTag = Some(chargeTokenId), ZonedDateTime.now, 1000, StopReason.Remote, Nil))
  send(StatusNotificationReq(ChargePointScope, status = ChargePointStatus.Available(), None, None))

  say("Transaction stopped")
  statusNotification(status = ChargePointStatus.Available())

} else {
  say("Authorization denied by Central System")
  fail("Not authorized")
}

// vim: set ts=4 sw=4 et:
