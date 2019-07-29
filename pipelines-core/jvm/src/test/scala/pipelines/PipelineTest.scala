package pipelines

import java.util.UUID

import monix.execution.CancelableFuture
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
      withScheduler { implicit scheduler =>
        val sink                           = DataSink.count()
        val Right(ints: Pipeline[_, Long]) = Pipeline.from(UUID.randomUUID, source, Nil, sink)
        ints.resultFuture.futureValue shouldBe 3

        val Right(strings: Pipeline[_, Long]) = Pipeline.from(UUID.randomUUID, source, Seq("asString" -> asString), sink)
        strings.resultFuture.futureValue shouldBe 3
      }
    }
  }

  "Pipeline.ChainStep.connect" should {
    "return a left w/ an error if the types don't match and success when they do" in {
      val invalidTransforms = Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get, stringToTryTuple).zipWithIndex.map {
        case (t, i) => s"step $i" -> t
      }
      val left = Pipeline.ChainStep.connect(source, invalidTransforms)

      val Left(actual) = left
      actual shouldBe "Can't connect source with type Int through 6 transforms as the types don't match: Int->Int->String->Tuple2[String,Try[Int]]->Try[Int]->Int: 'String -> Tuple2[String,Try[Int]]' can't be applied to 'Int'"

      val transforms = Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get).zipWithIndex.map {
        case (t, i) => s"step $i" -> t
      }
      val Right(newSource) = Pipeline.ChainStep.connect(source, transforms)
      newSource.last.output shouldBe ContentType.of[Int]

      withScheduler { implicit scheduler =>
        val obs  = Pipeline.createPipeline(source, newSource.tail)
        val list = obs.asObservable[Int].toListL.runSyncUnsafe(testTimeout)
        list shouldBe List(2, 4, 6)
      }
    }
  }
}
