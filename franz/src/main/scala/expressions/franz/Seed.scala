package expressions.franz


import scala.util.Try
import scala.util.control.NonFatal

final case class Seed(long: Long) {
  def next: Seed = Seed(long * 6364136223846793005L + 1442695040888963407L)

  def int(maxSigned: Int): Int = {
    val max = maxSigned.abs
    if (max == 0) {
      0
    } else {
      try {
        (long.abs % (max + 1)).toInt
      } catch {
        case NonFatal(e) =>
          throw new IllegalArgumentException(s"int failed w/ max=$max, long=$long, long.abs=${long.abs}, long.abs % (max + 1) is ${Try(long.abs % (max + 1))}", e)
      }
    }
  }
}

object Seed {
  def apply(init: Long = System.currentTimeMillis): Seed = new Seed(init)

  def nextInt(max: Int): State[Seed, Int] =
    State(seed => (seed.int(max), seed.next))

  /** @param percentile a value between 0.0 and 1.0. e.g. 0.25 should return true roughly 25% of the time
    * @return a boolean with the given percentage (between 0 and 1.0) of returning true
    */
  def weightedBoolean(percentile: Double): State[Seed, Boolean] = {
    require(percentile >= 0.0)
    require(percentile <= 1.0)
    nextDouble.map(_ < percentile)
  }

  val nextLong: State[Seed, Long] = State(seed => (seed.long,seed.next))

  /**
    * a state which returns a double between 0.0 and 1.0
    */
  val nextDouble: State[Seed, Double] = nextLong.map { i =>
    (i / Long.MaxValue.toDouble).abs
  }
  val nextBoolean: State[Seed, Boolean] = nextLong.map(_ > 0)

}
