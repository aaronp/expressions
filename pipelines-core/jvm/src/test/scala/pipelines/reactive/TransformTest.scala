package pipelines.reactive

import monix.reactive.Observable
import pipelines.BaseCoreTest

import scala.concurrent.duration._

class TransformTest extends BaseCoreTest with RepoTestData {

  "Transform._1.outputFor" should {
    "produce the type of the first tuple element" in {
      Transform._1.outputFor(ContentType.of[Int]) shouldBe None
      Transform._1.outputFor(ContentType.of[String]) shouldBe None
      Transform._1.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe Some(ContentType.of[String])
      Transform._1.outputFor(ContentType.of[(Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Int])
      Transform._1.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Double])
    }
  }
  "Transform._2.outputFor" should {
    "produce the type of the second tuple element" in {
      Transform._2.outputFor(ContentType.of[Int]) shouldBe None
      Transform._2.outputFor(ContentType.of[String]) shouldBe None
      Transform._2.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe Some(ContentType.of[Array[Byte]])
      Transform._2.outputFor(ContentType.of[(Int, String, Array[Byte])]) shouldBe Some(ContentType.of[String])
      Transform._2.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Int])
    }
  }
  "Transform._3.outputFor" should {
    "produce the type of the third tuple element" in {
      Transform._3.outputFor(ContentType.of[Int]) shouldBe None
      Transform._3.outputFor(ContentType.of[String]) shouldBe None
      Transform._3.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe None
      Transform._3.outputFor(ContentType.of[(String, Array[Byte], Int)]) shouldBe Some(ContentType.of[Int])
      Transform._3.outputFor(ContentType.of[(Int, String, BigDecimal)]) shouldBe Some(ContentType.of[BigDecimal])
      Transform._3.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[String])
    }
  }
  "Transform.map" should {
    val ints    = DataSource(Observable(1, 2, 3))
    val strings = DataSource(Observable("a", "b"))
    "only apply to content types for which it is valid" in {
      val toString: Transform = Transform.map[Int, String](_.toString)

      toString.appliesTo(ints) shouldBe true
      toString.appliesTo(strings) shouldBe false
    }

    "be able to transform into tuples, apply functions which work on tuples, and then back again" in {
      val stringToTuple = Transform.map { s: String =>
        (s, s.getBytes)
      }

      stringToTuple.outputFor(ContentType.of[Int]) shouldBe None
      stringToTuple.outputFor(ContentType.of[String]) shouldBe Some(ContentType.of[(String, Array[Byte])])
      stringToTuple.appliesTo(ints) shouldBe false
      stringToTuple.appliesTo(strings) shouldBe true

      val Some(stringAndByteArrayData) = stringToTuple.applyTo(strings)

      val Some(stringsAgain) = Transform._1.applyTo(stringAndByteArrayData)
      stringToTuple.appliesTo(stringsAgain) shouldBe true

      val Some(bytes) = Transform._2.applyTo(stringAndByteArrayData)
      stringToTuple.appliesTo(bytes) shouldBe false
    }
  }

  override def testTimeout = 2.seconds
}