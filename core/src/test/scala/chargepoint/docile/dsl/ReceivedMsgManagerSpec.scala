package chargepoint.docile.dsl

import com.thenewmotion.ocpp.messages.v1x._
import org.scalatest.flatspec.AnyFlatSpec
import scala.concurrent.ExecutionContext.Implicits.global

class ReceivedMsgManagerSpec extends AnyFlatSpec {

  "ReceivedMsgManager" should "pass on messages to those that requested them" in {
    val sut = receivedMsgManager()
    val testMsg = GenericIncomingMessage[
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ](StatusNotificationRes)

    val f = sut.dequeue(1)

    assert(!f.isCompleted)

    sut.enqueue(testMsg)

    f.map { dequeuedMsg =>
      assert(dequeuedMsg === List(testMsg))
    }
  }

  it should "remember incoming messages until someone dequeues them" in {
    val sut = receivedMsgManager()
    val testMsg = GenericIncomingMessage[
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ](StatusNotificationRes)

    sut.enqueue(testMsg)

    sut.dequeue(1) map { dequeuedMsgs =>
      assert(dequeuedMsgs === List(testMsg))
    }
  }

  it should "fulfill request for messages once enough are available" in {
    val sut = receivedMsgManager()
    sut.enqueue(testMsg(1))
    sut.enqueue(testMsg(2))

    val f = sut.dequeue(3)

    assert(!f.isCompleted)

    sut.enqueue(testMsg(3))

    f map { dequeuedMsgs =>
      assert(dequeuedMsgs === List(testMsg(1), testMsg(2), testMsg(3)))
    }
  }

  it should "allow a peek at what's in the queue" in {
    val sut = receivedMsgManager()
    sut.enqueue(testMsg(1))
    sut.enqueue(testMsg(2))

    assert(sut.currentQueueContents === List(testMsg(1), testMsg(2)))
  }

  it should "flush the queue" in {
    val sut = receivedMsgManager()
    sut.enqueue(testMsg(1))
    sut.enqueue(testMsg(2))

    assert(sut.currentQueueContents === List(testMsg(1), testMsg(2)))

    sut.flush()

    assert(sut.currentQueueContents.isEmpty)
  }

  private val testIdTagInfo = IdTagInfo(status = AuthorizationStatus.Accepted)

  private def receivedMsgManager() = new ReceivedMsgManager[
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ]

  private def testMsg(seqNo: Int) = GenericIncomingMessage[
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ](StartTransactionRes(
      transactionId = seqNo,
      idTag = testIdTagInfo
    ))
}
