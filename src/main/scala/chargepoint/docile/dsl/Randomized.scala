package chargepoint.docile.dsl

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

object Randomized {

  trait RandomizableOps[T] {
    def mulByFactor(left: T, right: Double): T
    def sum(left: T, right: T): T
  }

  implicit class RandomOps(rand: Random) {
    def randomize[T](v: T, randomFactor: Double = 0.5)(
        implicit ops: RandomizableOps[T]
    ): T = {
      val randomMultiplier = (rand.nextDouble() - 0.5) * 2.0 * randomFactor
      ops.sum(v, ops.mulByFactor(v, randomMultiplier))
    }
  }

  def randomize[T](v: T, randomFactor: Double = 0.5)(
      implicit
      rand: Random,
      ops: RandomizableOps[T]
  ): T = {
    rand.randomize(v, randomFactor)
  }

  implicit object RandomizableDurationOps extends RandomizableOps[Duration] {
    override def mulByFactor(left: Duration, right: Double): Duration = left mul right
    override def sum(left: Duration, right: Duration): Duration = left plus right
  }

  implicit object RandomizableFiniteDurationOps extends RandomizableOps[FiniteDuration] {
    override def mulByFactor(left: FiniteDuration, right: Double): FiniteDuration = (left mul right).asInstanceOf[FiniteDuration]
    override def sum(left: FiniteDuration, right: FiniteDuration): FiniteDuration = left plus right
  }

  implicit object RandomizableFloatOps extends RandomizableOps[Float] {
    override def mulByFactor(left: Float, right: Double): Float = (left * right).toFloat
    override def sum(left: Float, right: Float): Float = left + right
  }

  implicit object RandomizableDoubleOps extends RandomizableOps[Double] {
    override def mulByFactor(left: Double, right: Double): Double = left * right
    override def sum(left: Double, right: Double): Double = left + right
  }

  implicit object RandomizableIntOps extends RandomizableOps[Int] {
    override def mulByFactor(left: Int, right: Double): Int = Math.round(left * right).toInt
    override def sum(left: Int, right: Int): Int = left + right
  }

  implicit object RandomizableLongOps extends RandomizableOps[Long] {
    override def mulByFactor(left: Long, right: Double): Long = Math.round(left * right)
    override def sum(left: Long, right: Long): Long = left + right
  }
}
