// This example script shows how handlingIncomingMessages can be used to
// disregard unanticipated requests from the Central System during a test of
// remote start / stop functionality

import com.thenewmotion.ocpp.messages.v1x.meter.{Location, Measurand, Meter, ReadingContext, UnitOfMeasure, Value, ValueFormat}

handlingIncomingMessages(getConfigurationReq.respondingWith(GetConfigurationRes(List(), List()))) {
  handlingIncomingMessages(getLocalListVersionReq.respondingWith(GetLocalListVersionRes(AuthListNotSupported))) {

    bootNotification()

    for (i <- 0.to(2)) {
      statusNotification(scope = ConnectorScope(i))
    }
    
    say("Waiting for remote start message")
    val startRequest = expectIncoming(remoteStartTransactionReq.respondingWith(RemoteStartTransactionRes(true)))
    val chargeTokenId = startRequest.idTag
    val connector = startRequest.connector
    
    say("Received start request, starting...")
    statusNotification(scope = connector.get, status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))
    val transId = startTransaction(connector = connector.get, meterStart = 300, idTag = chargeTokenId).transactionId
    statusNotification(scope = connector.get, status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))
    
    say(s"Transaction started with ID $transId; sending MeterValues in 5s")
    
    Thread.sleep(5000)
    
    say("Sending MeterValues")
    
    val mv = Value(
      "5000",
      context = ReadingContext.SamplePeriodic,
      format = ValueFormat.Raw,
      measurand = Measurand.EnergyActiveImportRegister,
      phase = None,
      location = Location.Outlet,
      unit = UnitOfMeasure.Wh
    )
    meterValues(transactionId=Some(transId), meters = List(Meter(ZonedDateTime.now(), List(mv))))
    
    say("Waiting for remote stop...")
    
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
    
    // handle UnlockConnectorReq if present
    handlingIncomingMessages(unlockConnectorReq.respondingWith(UnlockConnectorRes(UnlockStatus.NotSupported))) {
      waitForValidRemoteStop()
      
      statusNotification(scope = connector.get, status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)))
      stopTransaction(transactionId = transId, idTag = Some(chargeTokenId))
      statusNotification(scope = connector.get, status = ChargePointStatus.Available())
    
      say("Transaction stopped")
    }
  }
}
