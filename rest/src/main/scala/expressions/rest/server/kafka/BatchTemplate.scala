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
         |import zio.duration.durationInt
         |
         |(_batchInput: expressions.rest.server.kafka.BatchInput) => {
         |  import _batchInput._
         |  import context._
         |  $script
         |}
       """.stripMargin

    expressions.CodeTemplate.compile[BatchInput, BatchResult](classOf[BatchInput].getName, codeBlock)
  }
}
