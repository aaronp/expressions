package expressions.rest.server.kafka

import expressions.Cache
import expressions.CodeTemplate.Compiled

import scala.util.Try

/**
  * The BatchTemplate is a specific compiler for scripts which take in a batch of messages and context
  * and return an IO of ... something to execute.
  */
object BatchTemplate {

  def cached = new Cache[OnBatch](compile)

  def compile(script: String): Try[Compiled[BatchInput, BatchResult]] = {

    val codeBlock = s"""
         |import expressions.implicits._
         |import expressions.DynamicJson
         |import expressions.template.Message
         |import expressions.rest.server.kafka.BatchContext
         |import zio._
         |import zio.console._
         |import io.circe.syntax._
         |
         |(_batchInput: expressions.rest.server.kafka.BatchInput) => {
         |  import _batchInput._
         |  import context._
         |  $script
         |}
       """.stripMargin

    expressions.CodeTemplate.compile[BatchInput, BatchResult]("expressions.rest.server.kafka.BatchInput", codeBlock)
  }

  object example {
//    import zio._
//
//    (_batchInput: expressions.rest.server.kafka.BatchInput) => {
//      import _batchInput._
//      import context._
//      val all: ZIO[Any, Throwable, Array[RecordMetadata]] = ZIO.foreach(batch) { msg =>
//        val bar   = msg.key.foo.bar.asString
//        val value = msg.content.value
//        bar.withValue(value).publishTo(config.producerTopic)
//      }
//      all.retryN(3).orDie
//    }
  }

  object counterExample {
    import expressions.DynamicJson
    import expressions.template.Message
    import io.circe.syntax._
    import zio.{ZEnv, ZIO}

    def onBatch(batch: Array[Message[DynamicJson, DynamicJson]], context: BatchContext): ZIO[ZEnv, Throwable, Unit] = {
      import context._

      // ==================================== this would be your script ====================================
      def publishAll(count: Int) = ZIO.foreach(batch) { msg =>
        val bar   = msg.key.foo.bar.asString
        val value = msg.content.xyz.value.deepMerge(Map("count" -> count).asJson)
        bar.withValue(value).publishTo(config.producerTopic)
      }

      for {
        count <- cache.modify { map =>
          val count = map.get("count") match {
            case Some(x: Int) => x + 1
            case _            => 1
          }
          count -> map.updated("count", count)
        }
        _ <- publishAll(count)
      } yield ZIO.unit
    }
  }
}
