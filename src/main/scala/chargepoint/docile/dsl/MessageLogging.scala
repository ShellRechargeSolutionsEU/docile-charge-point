package chargepoint.docile.dsl

import slogging.{Logger, LoggerFactory, StrictLogging}

trait MessageLogging extends StrictLogging {
  protected val incomingLogger: Logger = LoggerFactory.getLogger("| <<=")
  protected val outgoingLogger: Logger = LoggerFactory.getLogger("| =>>")
}
