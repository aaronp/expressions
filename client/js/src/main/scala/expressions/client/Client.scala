package expressions.client

import expressions.client.kafka.{ConsumerStats, PostRecord, StartedConsumer}
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{XMLHttpRequest, window}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * A JS client to our REST API
  */
object Client {
  val remoteHost         = s"${window.document.location.protocol}//${window.document.location.host}"
  val restServerLocation = s"$remoteHost/rest"
  val AppJson            = Map("Content-Type" -> "application/json")

  object mapping {
    def check(request: TransformRequest): Future[TransformResponse] = {
      val url = s"${restServerLocation}/mapping/check"
      Ajax.post(url, request.asJson.noSpaces, headers = AppJson).map { response: XMLHttpRequest =>
        io.circe.parser.decode[TransformResponse](response.responseText).toTry.get
      }
    }
  }
  object config {
    def get(): Future[String] = {
      Ajax.get(s"${restServerLocation}/config").map { response: XMLHttpRequest =>
        io.circe.parser.decode[Json](response.responseText).toTry.get.spaces4
      }
    }
    def listMappings(config: String) = {
      Ajax.post(s"${restServerLocation}/config/mappings/list", config, headers = AppJson).map { response =>
        io.circe.parser.decode[Map[String, List[String]]](response.responseText).toTry.get
      }
    }
  }

  object disk {
    def storeAt(path: String, value: String) = store(path.split("/", -1).toList, value)

    /**
      * @param path
      * @param value
      * @return true if created, false if updated (answers the question 'did this update?')
      */
    def store(path: List[String], value: String): Future[Boolean] = {
      val url = path.mkString(s"${restServerLocation}/store/", "/", "")
      Ajax.post(url, value, headers = AppJson).map { response =>
        response.status == 201
      }
    }
    def read(path: List[String]): Future[Option[String]] = {
      val url     = path.mkString(s"${restServerLocation}/store/get/", "/", "")
      val promise = Promise[Option[String]]()
      Ajax.get(url, headers = AppJson).onComplete {
        case Success(response) =>
          val opt = Option(response.responseText).filter(_ => response.status == 200)
          promise.tryComplete(Try(opt))
        case Failure(err) =>
          window.console.log(s"read returned $err")
          promise.tryComplete(Try(None))
      }
      promise.future
    }
    def list(path: List[String]): Future[List[String]] = {
      val url = path.mkString(s"${restServerLocation}/store/list/", "/", "")
      Ajax.get(url, headers = AppJson).map { response =>
        val asList = asJson(response).flatMap { json: Json =>
          json.as[List[String]].toTry
        }
        asList.get
      }
    }
  }

  object cache {
    def storeAt(path: String, value: String) = save(path.split("/", -1).toList, Json.fromString(value))

    /**
      * @param path
      * @param value
      * @return true if created, false if updated (answers the question 'did this update?')
      */
    def save(path: List[String], value: Json): Future[Boolean] = {
      val url = path.mkString(s"${restServerLocation}/cache/", "/", "")
      Ajax.post(url, value.noSpaces, headers = AppJson).map { response =>
        response.status == 201
      }
    }
    def read(path: List[String]): Future[Option[Json]] = {
      val url     = path.mkString(s"${restServerLocation}/cache/", "/", "")
      val promise = Promise[Option[Json]]()
      Ajax.get(url, headers = AppJson).onComplete {
        case Success(response) =>
          val opt = parse(response.responseText).toTry.toOption.filter(_ => response.status == 200)
          promise.tryComplete(Try(opt))
        case Failure(err) =>
          window.console.log(s"read returned $err")
          promise.tryComplete(Try(None))
      }
      promise.future
    }
  }

  object kafka {
    def publish(value: PostRecord): Future[Int] = {
      Ajax.post(s"${restServerLocation}/kafka/publish", value.asJson.noSpaces, headers = AppJson).map { response =>
        if (response.status == 200) {
          response.responseText.toInt
        } else -1
      }
    }
    def getDefault(): Future[Json] = {
      Ajax.get(s"${restServerLocation}/kafka/publish", headers = AppJson).map { response =>
        parse(response.responseText).toTry.get
      }
    }

    /** @param config
      * @return the started ID
      */
    def start(config: String): Future[String] = {
      val promise = Promise[String]()
      Ajax.post(s"${restServerLocation}/kafka/start", config, headers = AppJson).onComplete {
        case Success(response) =>
          promise.tryComplete(Try(response.responseText))
        case Failure(err) =>
          window.console.log(s"start returned $err")
          promise.tryComplete(Failure(err))
      }
      promise.future
    }
    def stop(id: String): Future[Boolean] = {
      val promise = Promise[Boolean]()
      Ajax.post(s"${restServerLocation}/kafka/stop/$id", headers = AppJson).onComplete {
        case Success(response: XMLHttpRequest) =>
          promise.tryComplete(asJson(response).flatMap(_.as[Boolean].toTry))
        case Failure(err) =>
          window.console.log(s"stop returned $err")
          promise.tryComplete(Failure(err))
      }
      promise.future
    }

    def running(): Future[List[StartedConsumer]] = {
      Ajax.get(s"${restServerLocation}/kafka/running", headers = AppJson).map { response =>
        asJson(response).flatMap(_.as[List[StartedConsumer]].toTry).get
      }
    }

    def stats(id: String): Future[Option[ConsumerStats]] = {
      Ajax.get(s"${restServerLocation}/kafka/stats/$id", headers = AppJson).map { response =>
        asJson(response).flatMap(_.as[Option[ConsumerStats]].toTry).get
      }
    }
  }

  object proxy {
    def makeRequest(input: HttpRequest): Future[HttpResponse] = {
//      Ajax(r.method.name, r.url, data = ByteBuffer.wrap(r.body), 0, headers = r.headers, false, "").map { resp =>
//        HttpResponse(resp.status, resp.responseText)
//      }
      val promise = Promise[HttpResponse]()
      Ajax.post(s"${restServerLocation}/proxy", input.asJson.noSpaces, headers = AppJson).onComplete {
        case Success(response: XMLHttpRequest) =>
          promise.tryComplete(asJson(response).flatMap(_.as[HttpResponse].toTry))
        case Failure(err) => promise.tryComplete(Failure(err))
      }
      promise.future
    }
  }

  def asJson(response: XMLHttpRequest) = {
    parse(response.responseText).toTry
  }
}
