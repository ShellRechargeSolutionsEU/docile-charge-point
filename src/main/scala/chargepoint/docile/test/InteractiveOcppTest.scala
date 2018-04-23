package chargepoint.docile
package test

import dsl._

class InteractiveOcppTest extends OcppTest {

  private val ops = new CoreOps with expectations.Ops with shortsend.Ops {
    def connectionData = InteractiveOcppTest.this.connectionData
  }

  private val promptCommands = new InteractiveOcppTest.PromptCommands {
    def connectionData = InteractiveOcppTest.this.connectionData
  }

  def run(): Unit = {

    val imports =
      """
        |import ops._
        |import promptCommands._
        |import com.thenewmotion.ocpp.messages._
        |
        |import scala.language.postfixOps
        |import scala.concurrent.duration._
        |import java.time._
        |
      """.stripMargin

    ammonite.Main(predefCode = imports).run(
      "ops" -> ops,
      "promptCommands" -> promptCommands
    )

    ()
  }
}

object InteractiveOcppTest {

  trait PromptCommands {

    protected def connectionData: OcppConnectionData

    def q: Unit =
      connectionData.receivedMsgManager.currentQueueContents foreach println

    def whoami: Unit =
      println(connectionData.chargePointIdentity)
  }
}
