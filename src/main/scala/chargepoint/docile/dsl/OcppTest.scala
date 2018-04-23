package chargepoint.docile.dsl

import java.net.URI

import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import chargepoint.docile.dsl.expectations.IncomingMessage
import com.thenewmotion.ocpp.Version
import com.thenewmotion.ocpp.json.api._
import com.thenewmotion.ocpp.messages.{ChargePointReq, ChargePointRes}
import slogging.LoggerFactory

trait OcppTest extends MessageLogging {
  private val connectionLogger = LoggerFactory.getLogger("connection")

  protected var connectionData: OcppConnectionData = _

  def runConnected(
    receivedMsgManager: ReceivedMsgManager,
    chargerId: String,
    endpoint: URI,
    version: Version,
    authKey: Option[String]
  ): Unit = {
    connect(receivedMsgManager, chargerId, endpoint, version, authKey)
    run()
    disconnect()
  }

  private def connect(
    receivedMsgManager: ReceivedMsgManager,
    chargerId: String,
    endpoint: URI,
    version: Version,
    authKey: Option[String]
  ): Unit = {

    connectionLogger.info(s"Connecting to OCPP v${version.name} endpoint $endpoint")

    val connection = new OcppJsonClient(chargerId, endpoint, List(version), authKey) {

      override def onDisconnect(): Unit = {
        connectionLogger.info(s"Gracefully disconnected from endpoint $endpoint")
        connectionData = connectionData.copy(ocppClient = None)
      }

      override def onError(e: OcppError): Unit = {
        connectionLogger.error(s"OCPP error received: $e")
      }

      override def requestHandler: ChargePointRequestHandler = {
        (req: ChargePointReq) =>
          incomingLogger.info(s"$req")

          val responsePromise = Promise[ChargePointRes]()

          def respond(res: ChargePointRes): Unit = {
            outgoingLogger.info(s"$res")
            responsePromise.success(res)
            ()
          }

          receivedMsgManager.enqueue(
            IncomingMessage(req, respond)
          )

          responsePromise.future
      }
    }

    connectionData = OcppConnectionData(Some(connection), receivedMsgManager, chargerId)
  }

  private def disconnect(): Unit = connectionData.ocppClient.foreach(_.close())

  protected def run(): Unit
}

case class OcppConnectionData(
  /**
    * The current OCPP connection
    *
    * This is a mutable Option[OcppJsonClient] instead of an immutable
    * OcppJsonClient because I hope this will allow us to write tests that
    * disconnect and reconnect when we have a more complete test DSL.
    */
  ocppClient: Option[OcppJsonClient],
  receivedMsgManager: ReceivedMsgManager,
  chargePointIdentity: String
)
