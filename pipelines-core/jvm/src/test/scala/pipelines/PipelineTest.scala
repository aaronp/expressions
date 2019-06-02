package pipelines

import monix.reactive.Observable
import pipelines.reactive.{ContentType, Transform}

import scala.util.Try

class PipelineTest extends BaseCoreTest {

  "Pipeline.connect" should {
    "return a left w/ an error if the types don't match" in {
      import pipelines.reactive.implicits._

      val double           = Transform.map[Int, Int](_ * 2)
      val asString         = Transform.map[Int, String](_.toString)
      val stringToTryTuple = Transform.map[String, (String, Try[Int])](s => s -> Try(s.toInt))

      val source = Observable(1, 2, 3).asDataSource()

      val left = Pipeline.connect(source, Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get, stringToTryTuple))
      left shouldBe Left(
        "Can't connect source with type Int through 6 transforms as the types don't match: Int->Int->String->Tuple2[String,Try[Int]]->Try[Int]->Int-> !ERROR! 'String -> Tuple2[String,Try[Int]]' can't be applied to 'Int'")

      val Right(newSource) = Pipeline.connect(source, Seq(double, asString, stringToTryTuple, Transform.tuples._2, Transform.tries.get))
      newSource.contentType shouldBe ContentType.of[Int]

      WithScheduler { implicit sched =>
        val list = newSource.asObservable.toListL.runSyncUnsafe(testTimeout)
        list shouldBe List(2, 4, 6)
      }
    }
  }
}
