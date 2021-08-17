package chargepoint.docile.dsl

import chargepoint.docile.dsl

import scala.language.higherKinds
import scala.concurrent.{Future, Promise}
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable

class ReceivedMsgManager[
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
] extends StrictLogging {

  import ReceivedMsgManager._

  type IncomingMessage = dsl.GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]

  private val messages = mutable.Queue[IncomingMessage]()

  private val waiters = mutable.Queue[Waiter[IncomingMessage]]()

  def enqueue(msg: IncomingMessage): Unit = synchronized {
    logger.debug(s"Enqueueing $msg")
    messages += msg
    tryToDeliver()
  }

  def dequeue(numMsgs: Int): Future[List[IncomingMessage]] = synchronized {
      logger.debug(s"Trying to dequeue $numMsgs")

      val promise = Promise[List[IncomingMessage]]()
      waiters += Waiter[IncomingMessage](promise, numMsgs)

      tryToDeliver()
      promise.future
  }

  def flush(): Unit = synchronized {
    messages.dequeueAll(_ => true)
    ()
  }

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
  private case class Waiter[T](promise: Promise[List[T]], numberOfMessages: Int)
}
