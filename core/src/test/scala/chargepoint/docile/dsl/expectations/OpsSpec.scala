package chargepoint.docile.dsl.expectations

import scala.collection.JavaConverters._
import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.global
import chargepoint.docile.dsl.{AwaitTimeout, AwaitTimeoutInMillis, CoreOps, DocileConnection, GenericIncomingMessage, IncomingError, IncomingRequest, IncomingResponse}
import com.thenewmotion.ocpp.Version1X
import com.thenewmotion.ocpp.VersionFamily.{V1X, V1XCentralSystemMessages, V1XChargePointMessages}
import com.thenewmotion.ocpp.json.api.OcppError
import com.thenewmotion.ocpp.messages.v1x._
import org.scalatest.flatspec.AnyFlatSpec

class OpsSpec extends AnyFlatSpec {

  "Ops" should "await for messages ignoring not matched" in {
    val mock = new MutableOpsMock()

    import mock.ops._

    implicit val awaitTimeout: AwaitTimeout = AwaitTimeoutInMillis(5000)

    mock send GetConfigurationReq(keys = List())
    mock send ClearCacheReq

    val result: Seq[ChargePointReq] = expectAllIgnoringUnmatched(
      clearCacheReq respondingWith ClearCacheRes(accepted = true)
    )

    assert(result === Seq(ClearCacheReq))
    assert(mock.responses.size === 1)
    assert(mock.responses.head === ClearCacheRes(accepted = true))
  }

  class MutableOpsMock {
    import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, TimeUnit}

    private val requestsQueue: BlockingQueue[GenericIncomingMessage[CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]] = new ArrayBlockingQueue[GenericIncomingMessage[CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]](1000)
    private val responsesQueue: BlockingQueue[ChargePointRes] = new ArrayBlockingQueue[ChargePointRes](1000)

    def responses: Iterable[ChargePointRes] = responsesQueue.asScala

    private def enqueueResponse(x: ChargePointRes): Unit = {
      responsesQueue.put(x)
    }

    def send(req: ChargePointReq): Unit = {
      requestsQueue.put(IncomingRequest[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](req, enqueueResponse))
    }

    def send(res: CentralSystemRes): Unit = {
      requestsQueue.put(IncomingResponse[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](res))
    }

    def sendError(err: OcppError): Unit = {
      requestsQueue.put(IncomingError[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](err))
    }

    val ops = new Ops[V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]
            with CoreOps[V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes] {
      implicit val csMessageTypes = V1XChargePointMessages
      implicit val csmsMessageTypes = V1XCentralSystemMessages
      implicit val executionContext = global

      override protected def connection: DocileConnection[
        V1X.type,
        Version1X,
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ] = {
        throw new AssertionError("This method should not be called")
      }

      override def awaitIncoming(num: Int)(implicit awaitTimeout: AwaitTimeout): Seq[IncomingMessage] = {
        for (_ <- 0 until num) yield {
          val value = requestsQueue.poll(awaitTimeout.toDuration.toMillis, TimeUnit.MILLISECONDS)
          if (value == null) {
            throw new TimeoutException("Failed to receive the message on time")
          }
          value
        }
      }
    }
  }
}
