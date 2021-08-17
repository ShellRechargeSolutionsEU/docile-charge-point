package chargepoint.docile
package dsl
package ocpp20transactions

import scala.concurrent.ExecutionContext
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.VersionFamily.{V20CsMessages, V20CsmsMessages}
import com.thenewmotion.ocpp.messages.v20._
import org.scalatest.flatspec.AnyFlatSpec

class OpsSpec extends AnyFlatSpec {

  "ocpp20transactions.Ops" should "generate a transaction UUID on transaction start" in {
    val (tx, _) = ops().startTransactionAtCablePluggedIn()

    assert(tx.data.id.matches("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}"))
  }

  it should "return transaction messages with incrementing sequence numbers" in {
    val (tx, req0) = ops().startTransactionAtCablePluggedIn()

    val req1 = tx.startEnergyOffer()
    val req2 = tx.end()

    assert((req0.seqNo, req1.seqNo, req2.seqNo) === ((0, 1, 2)))
  }

  it should "return transaction messages with incrementing sequence numbers over multiple transactions" in {
    val myOps = ops()
    val (tx0, req0) = myOps.startTransactionAtCablePluggedIn()

    val req1 = tx0.end()

    val (tx1, req2) = myOps.startTransactionAtAuthorized()

    val req3 = tx1.end()

    assert(List(req0, req1, req2, req3).map(_.seqNo) === 0.to(3).toList)
  }

  it should "specify the EVSE and connector ID on the first message, and not later" in {
    val (tx, req0) = ops().startTransactionAtAuthorized(evseId = 2, connectorId = 3)

    val req1 = tx.plugInCable()

    assert((req0.evse, req1.evse) === ((Some(EVSE(2, Some(3))), None)))
  }

  def ops(): Ops = new CoreOps[
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
