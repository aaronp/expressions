package expressions.client

import io.circe.syntax._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{XMLHttpRequest, window}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Client {

  def url = s"${window.document.location.protocol}//${window.document.location.host}/rest/mapping/check"

  def mappingCheck(request: TransformRequest): Future[TransformResponse] = {
    Ajax.post(url, request.asJson.noSpaces, headers = Map("Content-Type" -> "application/json")).map { response: XMLHttpRequest =>
      io.circe.parser.decode[TransformResponse](response.responseText).toTry.get
    }
  }
}
