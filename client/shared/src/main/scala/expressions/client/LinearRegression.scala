package expressions.client

import scala.language.implicitConversions

object LinearRegression {
  case class BestFit(a: Double, b: Double)

  case class Point(x: Double, y: Double)
  object Point {
    implicit def fromTuple(xy: (Int, Int)): Point = Point(xy._1, xy._2)
  }

  def stdDev[N : Numeric](values : List[N]) = {
    import Numeric.Implicits._
    val total = values.sum.toDouble
    val mean = total / values.size.toDouble
    val squares: Double = values.map(x => Math.pow(x.toDouble - mean, 2)).sum
    Math.sqrt(squares / values.size)
  }
  def apply(points: List[Point]): BestFit = {
    val xs   = points.map(_.x)
    val ys   = points.map(_.y)
    val aveX = xs.sum / points.size
    val aveY = points.map(_.y).sum / points.size
    val num = points.map {
      case Point(x, y) => (x - aveX) * (y - aveY)
    }.sum
    val denom = xs.map(x => (x - aveX) * (x - aveX)).sum

    val a: Double = num / denom
    val b         = aveY - (a * aveX)
    BestFit(a, b)
  }

  def correlationCoefficient(points: List[Point]): Double = {
    val xs   = points.map(_.x)
    val ys   = points.map(_.y)
    val aveX = xs.sum / points.size
    val aveY = points.map(_.y).sum / points.size
    val deviations = points.map {
      case Point(x, y) => (x - aveX) * (y - aveY)
    }.sum
    val xVariance = Math.sqrt(xs.map(x => Math.pow((x - aveX), 2)).sum)
    val yVariance = Math.sqrt(ys.map(y => Math.pow((y - aveY), 2)).sum)

    deviations / (xVariance * yVariance)
  }

  def main(a: Array[String]) = {
    println(correlationCoefficient(List(3, 4, 5).zip(List(8, 5, 8)).map(Point.fromTuple)))
    println(correlationCoefficient(List(2, 4, 5).zip(List(8, 3, 7)).map(Point.fromTuple)))

    val nums = """8319
                 |320
                 |12000
                 |4489
                 |12000
                 |13449
                 |10398
                 |7266
                 |10922
                 |6267
                 |9034
                 |8449
                 |12308
                 |4959
                 |12280
                 |9353
                 |8470
                 |6353
                 |69420
                 |10450
                 |12385
                 |5000
                 |12117
                 |10308
                 |5000
                 |5010
                 |6345
                 |10723
                 |23
                 |6873
                 |13926
                 |12713
                 |11718
                 |9967
                 |6675
                 |5000
                 |6085
                 |11560
                 |8635
                 |11422
                 |13809
                 |11819
                 |12005
                 |5804
                 |9751
                 |12416
                 |29232
                 |4411
                 |7726
                 |7300""".stripMargin.linesIterator.toList.map(_.toInt).ensuring(_.size == 50)
    println(s"std dev: ${stdDev(nums)}")
    println(s"max: ${nums.max}")
    println(s"min: ${nums.min}")
    println("Quartiles:")
    val sorted = nums.sorted
    val midpoint = nums.size / 2
    val quart = nums.size / 4
    println("1st quartile: " + sorted.take(quart).mkString(","))
    println("2nd quartile: " + sorted.slice(midpoint - quart, midpoint - quart + quart).mkString(","))
    println("3rd quartile: " + sorted.slice(midpoint, midpoint + quart).mkString(","))
    println("4th quartile: " + sorted.drop(midpoint + quart).mkString(","))
  }
  def main1(a: Array[String]) = {
    val xs = List(4, 3, 7, 2)
    val ys = List(7, 9, 1, 11)

    val rg = apply(xs.zip(ys).map(Point.fromTuple))
    println(rg)
  }
}
