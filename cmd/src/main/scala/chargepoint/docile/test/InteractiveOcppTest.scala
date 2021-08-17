package chargepoint.docile
package test

import chargepoint.docile.dsl._
import com.thenewmotion.ocpp.{Version, Version1X, VersionFamily}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20._
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}

import scala.concurrent.ExecutionContext.global
import scala.language.higherKinds

trait InteractiveOcppTest[VFam <: VersionFamily] {

  self: OcppTest[VFam] =>

  protected def importsSnippet: String =
    """
      |import ops._
      |import promptCommands._
      |import com.thenewmotion.ocpp.messages._
      |
      |import scala.language.postfixOps
      |import scala.concurrent.duration._
      |import scala.util.Random
      |import java.time._
      |
      |import chargepoint.docile.dsl.{AwaitTimeout, AwaitTimeoutInMillis, InfiniteAwaitTimeout}
      |import chargepoint.docile.dsl.Randomized._
      |
      |implicit val rand: Random = new Random()
      |
      """.stripMargin

}

abstract class InteractiveOcpp1XTest extends InteractiveOcppTest[VersionFamily.V1X.type] with Ocpp1XTest {
  val ops: Ocpp1XTest.V1XOps

  protected val promptCommands: InteractiveOcpp1XTest.V1XPromptCommands

  def run(defaultAwaitTimeout: AwaitTimeout): Unit = {

      val predefCode = importsSnippet +
                       s"""
                         | import com.thenewmotion.ocpp.messages.v1x._
                         |
                         | implicit val awaitTimeout: AwaitTimeout = ${defaultAwaitTimeout}
                       """.stripMargin

      ammonite.Main(predefCode = predefCode).run(
        "ops" -> ops,
        "promptCommands" -> promptCommands
      )

    ()
  }
}

object InteractiveOcpp1XTest {

  trait V1XPromptCommands extends InteractiveOcppTest.PromptCommands[
    VersionFamily.V1X.type,
    Version1X,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ]
}

abstract class InteractiveOcpp20Test extends InteractiveOcppTest[VersionFamily.V20.type] with Ocpp20Test {

  val ops: Ocpp20Test.V20Ops

  val promptCommands: InteractiveOcpp20Test.V20PromptCommands

  def run(defaultAwaitTimeout: AwaitTimeout): Unit = {

    val predefCode = importsSnippet +
      s"""
        | import com.thenewmotion.ocpp.messages.v20._
        |
        | implicit val awaitTimeout: AwaitTimeout = ${defaultAwaitTimeout}
      """.stripMargin

    ammonite.Main(predefCode = predefCode).run(
      "ops" -> ops,
      "promptCommands" -> promptCommands
    )

    ()
  }
}

object InteractiveOcpp20Test {

  trait V20PromptCommands extends InteractiveOcppTest.PromptCommands[
    VersionFamily.V20.type,
    Version.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ]
}

object InteractiveOcppTest {

  def apply(vfam: VersionFamily): OcppTest[vfam.type] = vfam match {
    case VersionFamily.V1X =>

      new InteractiveOcpp1XTest {

        private def connDat = connection

        implicit val csmsMessageTypes = VersionFamily.V1XCentralSystemMessages
        implicit val csMessageTypes = VersionFamily.V1XChargePointMessages
        implicit val executionContext = global

        val ops: Ocpp1XTest.V1XOps = new Ocpp1XTest.V1XOps
                with expectations.Ops[VersionFamily.V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]
                with shortsend.OpsV1X {
          def connection = connDat

          implicit val csmsMessageTypes = VersionFamily.V1XCentralSystemMessages
          implicit val csMessageTypes = VersionFamily.V1XChargePointMessages
          implicit val executionContext = global
        }

        val promptCommands: InteractiveOcpp1XTest.V1XPromptCommands = new InteractiveOcpp1XTest.V1XPromptCommands {
          def connection = connDat
        }
      }.asInstanceOf[OcppTest[vfam.type]]

    case VersionFamily.V20 =>

      new InteractiveOcpp20Test {

        private def connDat = connection

        implicit val csmsMessageTypes = VersionFamily.V20CsmsMessages
        implicit val csMessageTypes = VersionFamily.V20CsMessages
        implicit val executionContext = global

        val ops: Ocpp20Test.V20Ops = new Ocpp20Test.V20Ops
                with expectations.Ops[VersionFamily.V20.type, CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes] {
          def connection = connDat

          implicit val csmsMessageTypes = VersionFamily.V20CsmsMessages
          implicit val csMessageTypes = VersionFamily.V20CsMessages
          implicit val executionContext = global
        }

        val promptCommands: InteractiveOcpp20Test.V20PromptCommands = new InteractiveOcpp20Test.V20PromptCommands {
          def connection = connDat
        }
      }.asInstanceOf[OcppTest[vfam.type]]
  }

  trait PromptCommands[
    VFam <: VersionFamily,
    VersionBound <: Version,
    OutReq <: Request,
    InRes <: Response,
    OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
    InReq <: Request,
    OutRes <: Response,
    InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
  ] {

    protected def connection: DocileConnection[VFam, VersionBound, OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]

    def q: Unit =
      connection.receivedMsgManager.currentQueueContents foreach println

    def whoami: Unit =
      println(connection.chargePointIdentity)
  }
}
