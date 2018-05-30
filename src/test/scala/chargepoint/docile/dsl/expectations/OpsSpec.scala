package chargepoint.docile.dsl.expectations

import chargepoint.docile.dsl.{AwaitTimeout, CoreOps, OcppConnectionData}
import com.thenewmotion.ocpp.json.api.OcppError
import com.thenewmotion.ocpp.messages._
import org.specs2.mutable.Specification

import scala.collection.JavaConversions._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class OpsSpec extends Specification {

  "Ops" should {
    "await for messages ignoring not matched" in {
      val mock = new MutableOpsMock()

      import mock.ops._

      implicit val awaitTimeout: AwaitTimeout = AwaitTimeout(5.seconds)

      mock send GetConfigurationReq(keys = List())
      mock send ClearCacheReq

      val result: Seq[ChargePointReq] =

      expectAllIgnoringUnmatched(
        clearCacheReq respondingWith ClearCacheRes(accepted = true)
      )

      result must_=== Seq(ClearCacheReq)
      mock.responses.size must_=== 1
      mock.responses.head must_=== ClearCacheRes(accepted = true)
    }
  }

  class MutableOpsMock {
    import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, TimeUnit}

    private val requestsQueue: BlockingQueue[IncomingMessage] = new ArrayBlockingQueue[IncomingMessage](1000)
    private val responsesQueue: BlockingQueue[ChargePointRes] = new ArrayBlockingQueue[ChargePointRes](1000)

    def responses: Iterable[ChargePointRes] = responsesQueue.toIterable

    private def enqueueResponse(x: ChargePointRes): Unit = {
      responsesQueue.put(x)
    }

    def send(req: ChargePointReq): Unit = {
      requestsQueue.put(IncomingRequest(req, enqueueResponse))
    }

    def send(res: CentralSystemRes): Unit = {
      requestsQueue.put(IncomingResponse(res))
    }

    def sendError(err: OcppError): Unit = {
      requestsQueue.put(IncomingError(err))
    }

    val ops: Ops = new Ops with CoreOps {
      override protected def connectionData: OcppConnectionData = {
        throw new AssertionError("This method should not be called")
      }

      override def awaitIncoming(num: Int)(implicit awaitTimeout: AwaitTimeout): Seq[IncomingMessage] = {
        for (_ <- 0 until num) yield {
          val value = requestsQueue.poll(awaitTimeout.timeout.toMillis, TimeUnit.MILLISECONDS)
          if (value == null) {
            throw new TimeoutException("Failed to receive the message on time")
          }
          value
        }
      }
    }
  }
}
