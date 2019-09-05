package expressions

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class Stats(throughputsPerSecond: Seq[Long]) {
  val min  = throughputsPerSecond.min
  val max  = throughputsPerSecond.max
  val mean = throughputsPerSecond.sum / throughputsPerSecond.size.toDouble

  override def toString: String = s"min:$min/s, max:$max/s, ave:$mean/s, +/-${max - min}/s"
}
object Stats {

  def apply(times: Int, testLen: FiniteDuration = 5.seconds)(code: => Unit): Stats = {
    val throughput = (0 to times).map { _ =>
      val dealLine = testLen.fromNow
      var i        = 0
      while (dealLine.hasTimeLeft()) {
        code
        i = i + 1
      }
      i / testLen.toSeconds
    }

    Stats(throughput)
  }
}
