import com.thenewmotion.ocpp.messages._

say("Waiting for remote start message")
val startRequest = expectIncoming(remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)))
val chargeTokenId = startRequest.idTag

say("Received remote start, authorizing...")
val auth = authorize(chargeTokenId).idTag

if (auth.status == AuthorizationStatus.Accepted) {
  say("Obtained authorization from Central System; starting transaction")

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))
  val transId = startTransaction(meterStart = 300, idTag = chargeTokenId).transactionId
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))

  say(s"Transaction started with ID $transId; awaiting remote stop")

  def waitForValidRemoteStop(): Unit = {
    val shouldStop =
      expectIncoming(
        requestMatching({case r: RemoteStopTransactionReq => r.transactionId == transId})
          .respondingWith(RemoteStopTransactionRes(_))
      )

    if (shouldStop) {
      say("Received RemoteStopTransaction request; stopping transaction")
      ()
    } else {
      say(s"Received RemoteStopTransaction request for other transaction with ID. I'll keep waiting for a stop for $transId.")
      waitForValidRemoteStop()
    }
  }

  waitForValidRemoteStop()

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)))
  stopTransaction(transactionId = transId, idTag = Some(chargeTokenId))
  statusNotification(status = ChargePointStatus.Available())

  say("Transaction stopped")

} else {
  say("Authorization denied by Central System")
  fail("Not authorized")
}

// vim: set ts=4 sw=4 et:
