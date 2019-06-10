package pipelines.reactive

import monix.reactive.Observable
import pipelines.BaseCoreTest
import pipelines.reactive.implicits._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
class TransformTest extends BaseCoreTest with RepoTestData {

  def intPears              = Observable(1, 2, 3).map(x => (x, x.toString)).asDataSource()
  def intTuples: DataSource = Observable(1, 2, 3).map(x => (x.toString, x, x.toString * x)).asDataSource()
  def intQuads: DataSource  = Observable(1, 2, 3).map(x => (x.toString, x, x, x.toString * x)).asDataSource()

  "Transform.tries" should {
    "Transform any Try[T] to a [T]" in {
      Transform.tries.get.outputFor(ContentType.of[Try[Int]]) shouldBe Some(ContentType.of[Int])
      Transform.tries.get.outputFor(ContentType.of[Try[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.get.outputFor(ContentType.of[Failure[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.get.outputFor(ContentType.of[Success[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.get.outputFor(ContentType.of[String]) shouldBe None
      Transform.tries.get.outputFor(ContentType.of[List[String]]) shouldBe None

      import implicits._
      val data: Observable[Try[Int]] = Observable(Success(1), Success(2), Failure(new Exception("Bang")), Success(4))
      val Some(getSource)            = Transform.tries.get.applyTo(data.asDataSource())
      getSource.contentType shouldBe ContentType.of[Int]
      getSource.asObservable.take(2).toListL.runSyncUnsafe(testTimeout) shouldBe List(1, 2)
      val err = intercept[Exception] {
        getSource.asObservable.take(3).toListL.runSyncUnsafe(testTimeout)
      }
      err.getMessage shouldBe "Bang"
    }
  }
  "Transform.tries.successes" should {
    "Transform any Failure[T] to T" in {
      Transform.tries.successes.outputFor(ContentType.of[Try[Try[Int]]]) shouldBe Some(ContentType.of[Try[Int]])
      Transform.tries.successes.outputFor(ContentType.of[Try[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.successes.outputFor(ContentType.of[Failure[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.successes.outputFor(ContentType.of[Success[String]]) shouldBe Some(ContentType.of[String])
      Transform.tries.successes.outputFor(ContentType.of[String]) shouldBe None

      import implicits._
      val expected                   = new Exception("Bang")
      val data: Observable[Try[Int]] = Observable(Success(1), Success(2), Failure(expected), Success(4))
      val Some(getSource)            = Transform.tries.successes.applyTo(data.asDataSource())
      getSource.contentType shouldBe ContentType.of[Int]
      getSource.asObservable.take(3).toListL.runSyncUnsafe(testTimeout) shouldBe List(1, 2, 4)
    }
  }
  "Transform.tries.failures" should {
    "Transform any Failure[T] to an [Exception]" in {
      Transform.tries.failures.outputFor(ContentType.of[Try[String]]) shouldBe Some(ContentType.of[Throwable])
      Transform.tries.failures.outputFor(ContentType.of[Failure[String]]) shouldBe Some(ContentType.of[Throwable])
      Transform.tries.failures.outputFor(ContentType.of[Success[String]]) shouldBe Some(ContentType.of[Throwable])
      Transform.tries.failures.outputFor(ContentType.of[String]) shouldBe None

      import implicits._
      val expected                   = new Exception("Bang")
      val data: Observable[Try[Int]] = Observable(Success(1), Success(2), Failure(expected), Success(4))
      val Some(getSource)            = Transform.tries.failures.applyTo(data.asDataSource())
      getSource.contentType shouldBe ContentType.of[Throwable]
      getSource.asObservable.take(1).toListL.runSyncUnsafe(testTimeout) shouldBe List(expected)
    }
  }
  "Transform.tuples._1.outputFor" should {
    "produce the type of the first tuple element" in {

      intPears.contentType shouldBe (ClassType("Tuple2", Seq(ClassType("Int"), ClassType("String"))))
      intTuples.contentType shouldBe (ClassType("Tuple3", Seq(ClassType("String"), ClassType("Int"), ClassType("String"))))

      Transform.tuples._1.outputFor(ContentType.of[Int]) shouldBe None

      Transform.tuples._1.outputFor(ContentType.of[Int]) shouldBe None
      Transform.tuples._1.outputFor(ContentType.of[String]) shouldBe None
      Transform.tuples._1.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe Some(ContentType.of[String])
      Transform.tuples._1.outputFor(ContentType.of[(Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Int])
      Transform.tuples._1.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Double])

      Transform.tuples._1.applyTo(intPears).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List(1, 2, 3)
      Transform.tuples._1.applyTo(intTuples).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List("1", "2", "3")
    }
  }
  "Transform.tuples._2.outputFor" should {
    "produce the type of the second tuple element" in {
      Transform.tuples._2.outputFor(ContentType.of[Int]) shouldBe None
      Transform.tuples._2.outputFor(ContentType.of[String]) shouldBe None
      Transform.tuples._2.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe Some(ContentType.of[Array[Byte]])
      Transform.tuples._2.outputFor(ContentType.of[(Int, String, Array[Byte])]) shouldBe Some(ContentType.of[String])
      Transform.tuples._2.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Int])

      Transform.tuples._2.applyTo(intPears).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List("1", "2", "3")
      Transform.tuples._2.applyTo(intTuples).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List(1, 2, 3)
    }
  }
  "Transform.tuples._3.outputFor" should {
    "produce the type of the third tuple element" in {
      Transform.tuples._3.outputFor(ContentType.of[Int]) shouldBe None
      Transform.tuples._3.outputFor(ContentType.of[String]) shouldBe None
      Transform.tuples._3.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe None
      Transform.tuples._3.outputFor(ContentType.of[(String, Array[Byte], Int)]) shouldBe Some(ContentType.of[Int])
      Transform.tuples._3.outputFor(ContentType.of[(Int, String, BigDecimal)]) shouldBe Some(ContentType.of[BigDecimal])
      Transform.tuples._3.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[String])

      Transform.tuples._3.applyTo(intPears) shouldBe None
      Transform.tuples._3.applyTo(intTuples).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List("1", "22", "333")
    }
  }
  "Transform.tuples._4.outputFor" should {
    "produce the type of the third tuple element" in {
      Transform.tuples._4.outputFor(ContentType.of[Int]) shouldBe None
      Transform.tuples._4.outputFor(ContentType.of[String]) shouldBe None
      Transform.tuples._4.outputFor(ContentType.of[(String, Array[Byte])]) shouldBe None
      Transform.tuples._4.outputFor(ContentType.of[(String, Array[Byte], Int)]) shouldBe None
      Transform.tuples._4.outputFor(ContentType.of[(Int, String, BigDecimal)]) shouldBe None

      Transform.tuples._4.outputFor(ContentType.of[(Int, String, Float, BigDecimal)]) shouldBe Some(ContentType.of[BigDecimal])
      Transform.tuples._4.outputFor(ContentType.of[(Double, Int, String, Array[Byte])]) shouldBe Some(ContentType.of[Array[Byte]])

      Transform.tuples._4.applyTo(intPears) shouldBe None
      Transform.tuples._4.applyTo(intTuples) shouldBe None
      Transform.tuples._4.applyTo(intQuads).get.asObservable.toListL.runSyncUnsafe(testTimeout) shouldBe List("1", "22", "333")
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
      val stringToTuple: Transform = Transform.map { s: String =>
        (s, s.getBytes)
      }

      stringToTuple.outputFor(ContentType.of[Int]) shouldBe None
      stringToTuple.outputFor(ContentType.of[String]) shouldBe Some(ContentType.of[(String, Array[Byte])])
      stringToTuple.appliesTo(ints) shouldBe false
      stringToTuple.appliesTo(strings) shouldBe true

      val Some(stringAndByteArrayData) = stringToTuple.applyTo(strings)

      stringAndByteArrayData.contentType shouldBe ClassType("Tuple2", Seq(ClassType("String"), ClassType("Array", Seq(ClassType("Byte")))))

      val Some(stringsAgain) = Transform.tuples._1.applyTo(stringAndByteArrayData)
      stringToTuple.appliesTo(stringsAgain) shouldBe true

      val Some(bytes) = Transform.tuples._2.applyTo(stringAndByteArrayData)
      stringToTuple.appliesTo(bytes) shouldBe false

      val tupleList = stringAndByteArrayData.asObservable.toListL.runSyncUnsafe(testTimeout).toMap[String, Array[Byte]]
      tupleList.keySet shouldBe Set("a", "b")
      tupleList("a") should contain inOrderElementsOf ("a".getBytes)
      tupleList("b") should contain inOrderElementsOf ("b".getBytes)

      val stringsList = stringsAgain.asObservable.toListL.runSyncUnsafe(testTimeout)
      stringsList shouldBe List("a", "b")

      val bytesList: List[_] = bytes.asObservable.toListL.runSyncUnsafe(testTimeout)
      bytesList.size shouldBe 2
    }
  }

  override def testTimeout = 2.seconds
}
