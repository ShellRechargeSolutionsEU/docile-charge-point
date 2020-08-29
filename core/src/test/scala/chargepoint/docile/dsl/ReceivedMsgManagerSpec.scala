package chargepoint.docile.dsl

import com.thenewmotion.ocpp.messages.v1x._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.concurrent.ExecutionEnv

class ReceivedMsgManagerSpec(implicit ee: ExecutionEnv) extends Specification {

  "ReceivedMsgManager" should {

    "pass on messages to those that requested them" in {
      val sut = new ReceivedMsgManager[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ]
      val testMsg = GenericIncomingMessage[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](StatusNotificationRes)

      val f = sut.dequeue(1)

      f.isCompleted must beFalse

      sut.enqueue(testMsg)

      f must beEqualTo(List(testMsg)).await
    }

    "remember incoming messages until someone dequeues them" in {
      val sut = new ReceivedMsgManager[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ]
      val testMsg = GenericIncomingMessage[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](StatusNotificationRes)

      sut.enqueue(testMsg)

      sut.dequeue(1) must beEqualTo(List(testMsg)).await
    }

    "fulfill request for messages once enough are available" in new TestScope {
      sut.enqueue(testMsg(1))
      sut.enqueue(testMsg(2))

      val f = sut.dequeue(3)

      f.isCompleted must beFalse

      sut.enqueue(testMsg(3))

      f must beEqualTo(List(testMsg(1), testMsg(2), testMsg(3))).await
    }

    "allow a peek at what's in the queue" in new TestScope {
      sut.enqueue(testMsg(1))
      sut.enqueue(testMsg(2))

      sut.currentQueueContents mustEqual List(testMsg(1), testMsg(2))
    }

    "flush the queue" in new TestScope {
      sut.enqueue(testMsg(1))
      sut.enqueue(testMsg(2))

      sut.currentQueueContents mustEqual List(testMsg(1), testMsg(2))

      sut.flush()

      sut.currentQueueContents must beEmpty
    }
  }

  private trait TestScope extends Scope {
    val testIdTagInfo = IdTagInfo(status = AuthorizationStatus.Accepted)

    val sut = new ReceivedMsgManager[
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ]

    def testMsg(seqNo: Int) = GenericIncomingMessage[
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
}
