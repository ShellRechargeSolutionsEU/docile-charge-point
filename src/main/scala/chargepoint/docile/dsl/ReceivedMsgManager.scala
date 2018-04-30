package chargepoint.docile.dsl

import scala.concurrent.{Future, Promise}
import chargepoint.docile.dsl.expectations.IncomingMessage
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable

class ReceivedMsgManager extends StrictLogging {

  import ReceivedMsgManager._

  private val messages = mutable.Queue[IncomingMessage]()

  private val waiters = mutable.Queue[Waiter]()

  def enqueue(msg: IncomingMessage): Unit = synchronized {
    logger.debug(s"Enqueueing $msg")
    messages += msg
    tryToDeliver()
  }

  def dequeue(numMsgs: Int): Future[List[IncomingMessage]] = synchronized {
      logger.debug(s"Trying to dequeue $numMsgs")

      val promise = Promise[List[IncomingMessage]]()
      waiters += Waiter(promise, numMsgs)

      tryToDeliver()
      promise.future
  }

  def flush(): Unit = messages.dequeueAll(_ => true)

  def currentQueueContents: List[IncomingMessage] = synchronized {
    messages.toList
  }

  private def tryToDeliver(): Unit = {
    if (readyToDequeue) {
      logger.debug("delivering queued messages to expecters...")
      val waiter = waiters.dequeue

      val delivery = mutable.ArrayBuffer[IncomingMessage]()

      1.to(waiter.numberOfMessages) foreach { _ =>
        delivery += messages.dequeue()
      }

      logger.debug(s"delivering ${delivery.toList}")
      waiter.promise.success(delivery.toList)
      ()
    } else {
      logger.debug("Not ready to deliver")
    }
  }

  private def readyToDequeue: Boolean =
    waiters.headOption map (_.numberOfMessages) exists (_ <= messages.size)
}

object ReceivedMsgManager {
  private case class Waiter(promise: Promise[List[IncomingMessage]], numberOfMessages: Int)
}
