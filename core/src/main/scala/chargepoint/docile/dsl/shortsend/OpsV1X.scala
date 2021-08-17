package chargepoint.docile
package dsl
package shortsend

import java.time.ZonedDateTime
import scala.reflect.ClassTag
import com.thenewmotion.ocpp.messages.v1x._
import com.thenewmotion.ocpp.VersionFamily

trait OpsV1X {

  self: CoreOps[
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
 ] =>

  def authorize(idTag: String = "01020304")(
    implicit awaitTimeout: AwaitTimeout
  ): AuthorizeRes =
    sendSync(AuthorizeReq(idTag))

  def heartbeat()(
    implicit awaitTimeout: AwaitTimeout
  ): HeartbeatRes =
    sendSync(HeartbeatReq)

  def bootNotification(
    chargePointVendor: String = "The New Motion BV",
    chargePointModel: String = "Test Basic",
    chargePointSerialNumber: Option[String] = Some(connection.chargePointIdentity),
    chargeBoxSerialNumber: Option[String] = Some(connection.chargePointIdentity),
    firmwareVersion: Option[String] = Some("1.0.0"),
    iccid: Option[String] = None,
    imsi: Option[String] = None,
    meterType: Option[String] = None,
    meterSerialNumber: Option[String] = None
  )(
    implicit awaitTimeout: AwaitTimeout
  ): BootNotificationRes =
    sendSync(BootNotificationReq(
      chargePointVendor,
      chargePointModel,
      chargePointSerialNumber,
      chargeBoxSerialNumber,
      firmwareVersion,
      iccid,
      imsi,
      meterType,
      meterSerialNumber
    ))

  def dataTransfer(
    vendorId: String = "NewMotion",
    messageId: Option[String] = Some("MogrifyEspolusion"),
    data: Option[String] = None
  )(
    implicit awaitTimeout: AwaitTimeout
  ): CentralSystemDataTransferRes =
    sendSync(CentralSystemDataTransferReq(
      vendorId,
      messageId,
      data
     ))

  def diagnosticsStatusNotification(
    status: DiagnosticsStatus = DiagnosticsStatus.Idle
  )(
    implicit awaitTimeout: AwaitTimeout
  ): DiagnosticsStatusNotificationRes.type =
    sendSync(DiagnosticsStatusNotificationReq(
      status
    ))

  def firmwareStatusNotification(
    status: FirmwareStatus = FirmwareStatus.Idle
  )(
    implicit awaitTimeout: AwaitTimeout
  ): FirmwareStatusNotificationRes.type =
    sendSync(FirmwareStatusNotificationReq(
      status
    ))

  def meterValues(
    scope: Scope = ConnectorScope(0),
    transactionId: Option[Int] = None,
    meters: List[meter.Meter] = List(
      meter.Meter(
        timestamp = ZonedDateTime.now(),
        values = List(
          meter.Value(
            value = "10",
            measurand = meter.Measurand.CurrentImport,
            context = meter.ReadingContext.SamplePeriodic,
            format = meter.ValueFormat.Raw,
            phase = None,
            location = meter.Location.Outlet,
            unit = meter.UnitOfMeasure.Amp
          )
        )
      )
    )
  )(
    implicit awaitTimeout: AwaitTimeout
  ): MeterValuesRes.type =
    sendSync(MeterValuesReq(
      scope,
      transactionId,
      meters
    ))

  def startTransaction(
    connector: ConnectorScope = ConnectorScope(0),
    idTag: String = "ABCDEF01",
    timestamp: ZonedDateTime = ZonedDateTime.now,
    meterStart: Int = 0,
    reservationId: Option[Int] = None
  )(
    implicit awaitTimeout: AwaitTimeout
  ): StartTransactionRes =
    sendSync(StartTransactionReq(
      connector,
      idTag,
      timestamp,
      meterStart,
      reservationId
    ))

  def statusNotification(
    scope: ConnectorScope = ConnectorScope(0),
    status: ChargePointStatus = ChargePointStatus.Available(),
    timestamp: Option[ZonedDateTime] = Some(ZonedDateTime.now()),
    vendorId: Option[String] = None
  )(
    implicit awaitTimeout: AwaitTimeout
  ): StatusNotificationRes.type =
    sendSync(StatusNotificationReq(
      scope,
      status,
      timestamp,
      vendorId
    ))

  def stopTransaction(
    transactionId: Int = 0,
    idTag: Option[String] = Some("ABCDEF01"),
    timestamp: ZonedDateTime = ZonedDateTime.now,
    meterStop: Int = 16000,
    reason: StopReason = StopReason.Local,
    meters: List[meter.Meter] = List()
  )(
    implicit awaitTimeout: AwaitTimeout
  ): StopTransactionRes =
    sendSync(StopTransactionReq(
      transactionId,
      idTag,
      timestamp,
      meterStop,
      reason,
      meters
    ))

  def sendSync[REQ <: CentralSystemReq, RES <: CentralSystemRes : ClassTag](
    req: REQ
  )(
    implicit
    reqRes: CentralSystemReqRes[REQ, RES],
    awaitTimeout: AwaitTimeout
  ): RES = {
    self.send(req)
    self.expectIncoming(matching { case res: RES => res })
  }
}
