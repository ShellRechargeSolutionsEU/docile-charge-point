import java.time.ZonedDateTime
import chargepoint.docile.dsl._
import com.thenewmotion.ocpp.messages.v1x._
import scala.concurrent.duration._
import scala.util._

/**
 * Continuously listens to reserve now.
 */

say("Waiting for remote reserve now message")

val noTimeout: AwaitTimeout = InfiniteAwaitTimeout
val reserveRequest = expectIncoming(requestMatching ( {case r: ReserveNowReq => }).respondingWith(ReserveNowRes(Reservation.Accepted)))(noTimeout)

val chargeTokenId = "04EC2CC2552280"
say(s"Received reserve now, authorizing... $chargeTokenId")

say("Received reserve now, authorizing...")
val auth = authorize(chargeTokenId).idTag

say(s"Received status $auth.status")
if (auth.status == AuthorizationStatus.Accepted) {
  say("Obtained authorization from Central System;  reserve now")
  Thread.sleep(2000) // to simulate real chargepoint behaviour
  statusNotification(status = ChargePointStatus.Reserved(), scope = ConnectorScope(0))

} else {
  say("Authorization denied by Central System")
  fail("Not authorized")
}
