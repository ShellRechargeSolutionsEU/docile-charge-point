package chargepoint.docile
package dsl
package shortsend

import java.time.Instant
import scala.reflect.ClassTag
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.messages.v20._

trait OpsV20 {

  self: CoreOps[
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
 ] =>

  def authorize(
    evseIds: List[Int] = List.empty[Int],
    idToken: IdToken = defaultIdToken,
    ocspRequestData: List[OCSPRequestData] = List.empty[OCSPRequestData]
  )(
    implicit awaitTimeout: AwaitTimeout
  ): AuthorizeResponse =
    sendSync(AuthorizeRequest(noneIfEmpty(evseIds), idToken, noneIfEmpty(ocspRequestData)))

  def heartbeat()(
    implicit awaitTimeout: AwaitTimeout
  ): HeartbeatResponse =
    sendSync(HeartbeatRequest())

  def bootNotification(
    chargingStation: ChargingStation = ChargingStation(
      serialNumber = Some(connection.chargePointIdentity),
      model = "Docile",
      vendorName = "The New Motion BV",
      firmwareVersion = Some("1.0.0"),
      modem = None
    ),
    reason: BootReason = BootReason.PowerUp
  )(
    implicit awaitTimeout: AwaitTimeout
  ): BootNotificationResponse =
    sendSync(BootNotificationRequest(chargingStation, reason))

  def transactionEvent(
    eventType: TransactionEvent = TransactionEvent.Started,
    meterValue: List[MeterValue] = List.empty[MeterValue],
    timestamp: Instant = Instant.now(),
    triggerReason: TriggerReason = TriggerReason.EVDetected,
    seqNo: Int,
    offline: Option[Boolean] = None,
    numberOfPhasesUsed: Option[Int] = None,
    cableMaxCurrent: Option[BigDecimal] = None,
    reservationId: Option[Int] = None,
    transactionData: Transaction = Transaction(
      id = "transactie-1",
      chargingState = Some(ChargingState.EVDetected),
      timeSpentCharging = None,
      stoppedReason = None,
      remoteStartId = None
    ),
    evse: Option[EVSE] = Some(EVSE(1, Some(1))),
    idToken: Option[IdToken] = Some(defaultIdToken)
  )(
    implicit awaitTimeout: AwaitTimeout
  ): TransactionEventResponse =
    sendSync(TransactionEventRequest(
      eventType,
      noneIfEmpty(meterValue),
      timestamp,
      triggerReason,
      seqNo,
      offline,
      numberOfPhasesUsed,
      cableMaxCurrent,
      reservationId,
      transactionData,
      evse,
      idToken
    ))

  def statusNotification(
    timestamp: Instant = Instant.now(),
    connectorStatus: ConnectorStatus = ConnectorStatus.Available,
    evseId: Int = 1,
    connectorId: Int = 1
  )(
    implicit awaitTimeout: AwaitTimeout
  ): StatusNotificationResponse =
    sendSync(StatusNotificationRequest(
      timestamp,
      connectorStatus,
      evseId,
      connectorId
    ))

  def sendSync[REQ <: CsmsRequest, RES <: CsmsResponse : ClassTag](
    req: REQ
  )(
    implicit
    reqRes: CsmsReqRes[REQ, RES],
    awaitTimeout: AwaitTimeout
  ): RES = {
    self.send(req)
    self.expectIncoming(matching { case res: RES => res })
  }

  private def noneIfEmpty[T](s: List[T]): Option[List[T]] =
    if (s.isEmpty) None else Some(s)

  private val defaultIdToken =
    IdToken(
      idToken  = "01020304",
      `type` = IdTokenType.ISO14443,
      additionalInfo = None
    )
}
