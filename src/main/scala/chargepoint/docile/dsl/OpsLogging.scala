package chargepoint.docile.dsl

import slogging.{Logger, LoggerFactory, StrictLogging}

trait OpsLogging extends StrictLogging {
  protected val opsLogger: Logger = LoggerFactory.getLogger("operation")
}
