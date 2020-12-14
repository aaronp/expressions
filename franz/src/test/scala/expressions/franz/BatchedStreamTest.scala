package expressions.franz

import zio.{Managed, Ref, UIO}

class BatchedStreamTest extends BaseFranzTest {

  "BatchedStream" should {
    "work" in {
      dockerenv.kafka().bracket {

        val list = Managed.make(Ref.make(List.empty[KafkaRecord]))(_ => UIO.unit)
        for {
          recordsRef <- Ref.make(List.empty[KafkaRecord])
          resources = BatchedStream(FranzConfig()) { d8a: Array[KafkaRecord] =>
            recordsRef.update(d8a ++: _)
          }
        } yield ()

//        val testCase = .use { stream =>
//          ???
//        }
      }
    }
  }

}
