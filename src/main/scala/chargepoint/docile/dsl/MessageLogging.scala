package chargepoint.docile.dsl

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory


trait MessageLogging {
  protected val incomingLogger: Logger = Logger(LoggerFactory.getLogger("| <<="))
  protected val outgoingLogger: Logger = Logger(LoggerFactory.getLogger("| =>>"))
}
