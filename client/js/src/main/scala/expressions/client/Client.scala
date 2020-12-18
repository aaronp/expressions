package expressions.client

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{XMLHttpRequest, window}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Client {
  val remoteHost = s"${window.document.location.protocol}//${window.document.location.host}/rest"
  val AppJson    = Map("Content-Type" -> "application/json")

  object mapping {
    def check(request: TransformRequest): Future[TransformResponse] = {
      val url = s"${remoteHost}/mapping/check"
      Ajax.post(url, request.asJson.noSpaces, headers = AppJson).map { response: XMLHttpRequest =>
        io.circe.parser.decode[TransformResponse](response.responseText).toTry.get
      }
    }
  }
  object config {
    def get(): Future[String] = {
      Ajax.get(s"${remoteHost}/config").map { response: XMLHttpRequest =>
        io.circe.parser.decode[Json](response.responseText).toTry.get.spaces4
      }
    }
    def listMappings(config: String) = {
      Ajax.post(s"${remoteHost}/config/mappings/list", config, headers = AppJson).map { response =>
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
      val url = path.mkString(s"${remoteHost}/store/", "/", "")
      Ajax.post(url, value, headers = AppJson).map { response =>
        response.status == 201
      }
    }
    def read(path: List[String]): Future[Option[String]] = {
      val url = path.mkString(s"${remoteHost}/store/", "/", "")
      Ajax.get(url, headers = AppJson).map { response =>
        Option(response.responseText).filter(_ => response.status == 200)
      }
    }
  }

  object cache {
    def storeAt(path: String, value: String) = save(path.split("/", -1).toList, value)

    /**
      * @param path
      * @param value
      * @return true if created, false if updated (answers the question 'did this update?')
      */
    def save(path: List[String], value: Json): Future[Boolean] = {
      val url = path.mkString(s"${remoteHost}/cache/", "/", "")
      Ajax.post(url, value.noSpaces, headers = AppJson).map { response =>
        response.status == 201
      }
    }
    def read(path: List[String]): Future[Option[Json]] = {
      val url = path.mkString(s"${remoteHost}/cache/", "/", "")
      Ajax.get(url, headers = AppJson).map { response =>
        parse(response.responseText).toTry.toOption.filter(_ => response.status == 200)
      }
    }
  }
}
