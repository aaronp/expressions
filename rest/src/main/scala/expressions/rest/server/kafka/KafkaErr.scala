package expressions.rest.server.kafka

import com.typesafe.scalalogging.LazyLogging
import expressions.CodeTemplate.Expression
import expressions.client.{HttpRequest, HttpResponse}
import expressions.rest.server.JsonMsg
import zio.kafka.consumer.CommittableRecord
import zio.{Task, ZIO}

sealed trait KafkaErr extends Exception with LazyLogging {
  def fail: Task[Nothing] = {
    Task.fail(this)
  }
  def widen: KafkaErr = {
    logger.error(s"SINK ERROR: ${this}")
    this
  }
}
object KafkaErr {

  def conversionError(record: CommittableRecord[_, _], exp: Throwable)        = ConvertToJsonError(record, exp).widen
  def compileExpressionError(record: CommittableRecord[_, _], exp: Throwable) = CompileExpressionError(record, exp).widen
  def expressionError(record: CommittableRecord[_, _], expr: Expression[JsonMsg, Seq[HttpRequest]], exp: Throwable) =
    ExpressionError(record, expr, exp).widen
  def restError(record: CommittableRecord[_, _], failed: Seq[(HttpRequest, HttpResponse)]) =
    RestError(record, failed).widen

  def coords(r: CommittableRecord[_, _]) = s"{${r.record.topic()}:${r.partition}/${r.offset.offset}@${r.key}}"

  case class ConvertToJsonError(record: CommittableRecord[_, _], exception: Throwable)
      extends Exception(s"Couldn't do json conversion for ${coords(record)}: ${exception.getMessage}", exception)
      with KafkaErr
  case class CompileExpressionError(record: CommittableRecord[_, _], exception: Throwable)
      extends Exception(s"Couldn't create an expression for ${coords(record)}: ${exception.getMessage}\n", exception)
      with KafkaErr
  case class ExpressionError(record: CommittableRecord[_, _], expr: Expression[JsonMsg, Seq[HttpRequest]], exception: Throwable)
      extends Exception(s"Couldn't execute expression $expr for ${coords(record)}: ${exception.getMessage}", exception)
      with KafkaErr
  case class RestError(record: CommittableRecord[_, _], failed: Seq[(HttpRequest, HttpResponse)])
      extends Exception(s"Not all rest results returned successfully: ${failed}")
      with KafkaErr

  def validate(record: CommittableRecord[_, _], results: Seq[(HttpRequest, HttpResponse)]): ZIO[Any, Throwable, Unit] = {
    restError(record, results).fail.unless(results.map(_._2.statusCode).forall(_ == 200))
  }
}
