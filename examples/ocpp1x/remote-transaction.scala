import com.thenewmotion.ocpp.messages._

say("Waiting for remote start message")
val startRequest = expectIncoming(remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)))
val chargeTokenId = startRequest.idTag

say("Received remote start, authorizing...")
val auth = authorize(chargeTokenId).idTag

if (auth.status == AuthorizationStatus.Accepted) {
  say("Obtained authorization from Central System; starting transaction")

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)), timestamp=Some(ZonedDateTime.now.withNano(0)))
  val transId = startTransaction(meterStart = 300, idTag = chargeTokenId, timestamp=ZonedDateTime.now.withNano(0)).transactionId
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)), timestamp=Some(ZonedDateTime.now.withNano(0)))

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
  // handle UnlockConnectorReq if present
  Try(expectIncoming(unlockConnectorReq.respondingWith(UnlockConnectorRes(UnlockStatus.NotSupported))))
  
  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)), timestamp=Some(ZonedDateTime.now.withNano(0)))
  stopTransaction(transactionId = transId, idTag = Some(chargeTokenId), timestamp=ZonedDateTime.now.withNano(0))
  statusNotification(status = ChargePointStatus.Available(), timestamp=Some(ZonedDateTime.now.withNano(0)))

  say("Transaction stopped")

} else {
  say("Authorization denied by Central System")
  fail("Not authorized")
}

// vim: set ts=4 sw=4 et:
