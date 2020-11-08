package chargepoint.docile
package dsl
package ocpp20transactions

import scala.concurrent.ExecutionContext
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.VersionFamily.{V20CsMessages, V20CsmsMessages}
import com.thenewmotion.ocpp.messages.v20._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class OpsSpec extends Specification {

  "ocpp20transactions.Ops" should {

    "generate a transaction UUID on transaction start" in new TestScope {
      val (tx, _) = startTransactionAtCablePluggedIn()

      tx.data.id must beMatching("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}")
    }

    "return transaction messages with incrementing sequence numbers" in new TestScope {
      val (tx, req0) = startTransactionAtCablePluggedIn()

      val req1 = tx.startEnergyOffer()
      val req2 = tx.end()

      (req0.seqNo, req1.seqNo, req2.seqNo) mustEqual ((0, 1, 2))
    }

    "return transaction messages with incrementing sequence numbers over multiple transactions" in new TestScope {
      val (tx0, req0) = startTransactionAtCablePluggedIn()

      val req1 = tx0.end()

      val (tx1, req2) = startTransactionAtAuthorized()

      val req3 = tx1.end()

      List(req0, req1, req2, req3).map(_.seqNo) mustEqual 0.to(3).toList
    }

    "specify the EVSE and connector ID on the first message, and not later" in new TestScope {
      val (tx, req0) = startTransactionAtAuthorized(evseId = 2, connectorId = 3)

      val req1 = tx.plugInCable()

      (req0.evse, req1.evse) mustEqual ((Some(EVSE(2, Some(3))), None))
    }
  }

  trait TestScope extends Scope with CoreOps[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with Ops {
    protected lazy val connection = sys.error("This test should not do anything with the OCPP connection")
    val csmsMessageTypes = V20CsmsMessages
    val csMessageTypes = V20CsMessages
    implicit val executionContext = ExecutionContext.global
  }
}
