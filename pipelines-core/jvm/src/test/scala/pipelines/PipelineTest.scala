package pipelines

import java.util.UUID

import monix.reactive.Observable
import org.scalatest.concurrent.ScalaFutures
import pipelines.reactive.{ContentType, DataSink, Transform}

import scala.util.Try

class PipelineTest extends BaseCoreTest with ScalaFutures {
  import pipelines.reactive.implicits._

  val double           = Transform.map[Int, Int](_ * 2)
  val asString         = Transform.map[Int, String](_.toString)
  val stringToTryTuple = Transform.map[String, (String, Try[Int])](s => s -> Try(s.toInt))

  val source = Observable(1, 2, 3).asDataSource()

  "Pipeline.from" should {
    "connect any source w/ a generic sink" in {
      WithScheduler { implicit scheduler =>
        val sink                        = DataSink.count()
        val Right(ints: Pipeline[Long]) = Pipeline.from(UUID.randomUUID, source, Nil, sink)
        ints.result.futureValue shouldBe 3

        val Right(strings: Pipeline[Long]) = Pipeline.from(UUID.randomUUID, source, Seq(asString), sink)
        strings.result.futureValue shouldBe 3
      }
    }
  }

  "Pipeline.connect" should {
    "return a left w/ an error if the types don't match and success when they do" in {
      val left = Pipeline.connect(source, Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get, stringToTryTuple))

      val Left(actual) = left

      actual shouldBe "Can't connect source with type Int through 6 transforms as the types don't match: Int->Int->String->Tuple2[String,Try[Int]]->Try[Int]->Int-> !ERROR! 'String -> Tuple2[String,Try[Int]]' can't be applied to 'Int'"

      val Right(newSource) = Pipeline.connect(source, Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get))
      newSource.contentType shouldBe ContentType.of[Int]

      WithScheduler { implicit scheduler =>
        val list = newSource.asObservable.toListL.runSyncUnsafe(testTimeout)
        list shouldBe List(2, 4, 6)
      }
    }
  }
}
