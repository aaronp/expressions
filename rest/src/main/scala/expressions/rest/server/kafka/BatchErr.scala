package expressions.rest.server.kafka

import com.typesafe.scalalogging.LazyLogging
import expressions.CodeTemplate.Expression
import expressions.client.{HttpRequest, HttpResponse}
import expressions.rest.server.JsonMsg
import zio.Task
import zio.kafka.consumer.CommittableRecord

sealed trait BatchErr extends Exception with LazyLogging {
  def fail: Task[Nothing] = {
    Task.fail(this)
  }
  def widen: BatchErr = {
    logger.error(s"SINK ERROR: ${this}")
    this
  }

}

object BatchErr {
  //  case class ConvertToJsonError(batch: Batch, exception: Throwable)
  //    extends Exception(s"Couldn't do json conversion for ${coords(record)}: ${exception.getMessage}", exception)
  //      with BatchErr
  case class CompileExpressionError(batch: Batch, exception: Throwable)
      extends Exception(s"Couldn't create an expression for ${batch}: ${exception.getMessage}\n", exception)
      with BatchErr
  case class ExpressionError(batch: Batch, expr: Expression[JsonMsg, Seq[HttpRequest]], exception: Throwable)
      extends Exception(s"Couldn't execute expression $expr for ${batch}: ${exception.getMessage}", exception)
      with BatchErr
  case class RestError(record: CommittableRecord[_, _], failed: Seq[(HttpRequest, HttpResponse)])
      extends Exception(s"Not all rest results returned successfully: ${failed}")
      with BatchErr

}
