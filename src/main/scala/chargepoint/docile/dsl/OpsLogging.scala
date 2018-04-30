package chargepoint.docile.dsl

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory


trait OpsLogging {
  protected val opsLogger: Logger = Logger(LoggerFactory.getLogger("operation"))
}
