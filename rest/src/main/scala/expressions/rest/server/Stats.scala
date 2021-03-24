package expressions.rest.server

import expressions.client.kafka.{ConsumerStats, RecordCoords, RecordSummary}
import expressions.client.{HttpRequest, HttpResponse}
import expressions.franz.SchemaGen
import expressions.rest.server.kafka.KafkaSink.RunningSinkId
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.avro.generic.IndexedRecord
import zio.kafka.consumer.CommittableRecord

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object Stats {

  sealed trait RecordBody

  case class Base64Body(base64Data: String) extends RecordBody
  case class JasonBody(json: Json)          extends RecordBody

  object RecordBody {
    def apply(record: CommittableRecord[_, _]) = {}
  }

  def updateStats(consumerStats: ConsumerStats, record: CommittableRecord[_, _], result: Try[Seq[(HttpRequest, HttpResponse)]], now: Long): ConsumerStats = {
    val summary = RecordSummary(
      recordCoords(record),
      asMessage(result),
      asJson(result),
      now
    )
    val errors = result match {
      case Failure(_) => summary +: consumerStats.errors
      case _          => consumerStats.errors
    }
    val newRecords: Seq[RecordSummary] = summary +: consumerStats.recentRecords.take(20)
    consumerStats.copy(totalRecords = consumerStats.totalRecords + 1, recentRecords = newRecords, errors = errors.take(20))
  }

  def createStats(id: RunningSinkId, record: CommittableRecord[_, _], result: Try[Seq[(HttpRequest, HttpResponse)]], now: Long) = {
    val summary = RecordSummary(
      recordCoords(record),
      asMessage(result),
      asJson(result),
      now
    )
    val errors = result match {
      case Failure(_) => List(summary)
      case _          => Nil
    }
    ConsumerStats(id, 1, List(summary), errors)
  }

  private def recordCoords(record: CommittableRecord[_, _]): RecordCoords = RecordCoords(record.record.topic(), record.offset.offset, record.partition, asString(record.key))

  private def asMessage(request: HttpRequest, response: HttpResponse): String = {
    s"${request.url} yields ${response.statusCode}"
  }

  private def asMessage(results: Try[Seq[(HttpRequest, HttpResponse)]]): String = {
    results match {
      case Failure(err)                        => s"Error: $err"
      case Success((request, response) :: Nil) => asMessage(request, response)
      case Success(list) =>
        list
          .map {
            case (request, response) => asMessage(request, response)
          }
          .mkString(s"${list.size} requests: [\n", "\n", "\n]")
    }
  }

  private def asJson(results: Try[Seq[(HttpRequest, HttpResponse)]]): Json = {
    results match {
      case Failure(err)                        => Json.obj("error" -> s"${err}".asJson)
      case Success((request, response) :: Nil) => asJson(request, response)
      case Success(list) =>
        val jsons = list.map {
          case (request, response) => asJson(request, response)
        }
        jsons.asJson
    }
  }

  private def asJson(request: HttpRequest, response: HttpResponse): Json = {
    Map(
      "request"  -> request.asJson,
      "response" -> asJson(response)
    ).asJson
  }

  private def asJson(response: HttpResponse): Json = {
    Map(
      "code" -> response.statusCode.asJson,
      "body" -> response.body.asJson
    ).asJson
  }

  @tailrec
  private def asString(value: Any): String = {
    value match {
      case null                  => "NULL"
      case jason: Json           => jason.asString.getOrElse(jason.noSpaces)
      case record: IndexedRecord => asString(SchemaGen.asJson(record))
      case other                 => other.toString
    }

  }
}
