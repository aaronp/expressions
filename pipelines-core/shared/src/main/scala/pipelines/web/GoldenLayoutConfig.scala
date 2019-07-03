package pipelines.web

import io.circe.Decoder.Result
import io.circe._

// type is 'row', 'column' or 'stack'
case class GoldenLayoutSettings(selectionEnabled : Boolean)
object GoldenLayoutSettings {
  implicit val encoder: ObjectEncoder[GoldenLayoutSettings] = io.circe.generic.semiauto.deriveEncoder[GoldenLayoutSettings]
  implicit val decoder: Decoder[GoldenLayoutSettings] = io.circe.generic.semiauto.deriveDecoder[GoldenLayoutSettings]

}

case class GoldenLayoutConfig(`type`: String, content: List[GoldenLayoutConfig], componentName: String, componentState: Json) {
  def +:(child: GoldenLayoutConfig): GoldenLayoutConfig = copy(content = child +: content)
  def :+(child: GoldenLayoutConfig): GoldenLayoutConfig = copy(content = content :+ child)

  def toConfigJson(settings : GoldenLayoutSettings = GoldenLayoutSettings(true)): Json = {
    import io.circe.syntax._
    val me = GoldenLayoutConfig.encoder(this)
    Json.obj("content" -> Json.arr(me), "settings" -> settings.asJson)
  }
}

object GoldenLayoutConfig {
  def row(content: List[GoldenLayoutConfig] = Nil)                     = new GoldenLayoutConfig("row", content, "", Json.Null)
  def column(content: List[GoldenLayoutConfig] = Nil)                  = new GoldenLayoutConfig("column", content, "", Json.Null)
  def stack(content: List[GoldenLayoutConfig] = Nil)                   = new GoldenLayoutConfig("stack", content, "", Json.Null)
  def component(componentName: String, componentState: Json = Json.Null) = new GoldenLayoutConfig("component", Nil, componentName, componentState)

  implicit val encoder: ObjectEncoder[GoldenLayoutConfig] = io.circe.generic.semiauto.deriveEncoder[GoldenLayoutConfig].mapJsonObject { json =>
    val obj = json.filter {
      case ("componentName", name) => name.asString.fold(false)(_.nonEmpty)
      case ("content", content)    => content.asArray.fold(false)(_.nonEmpty)
      case ("componentState", st8) => !st8.isNull
      case _                       => true
    }

    obj
  }
  implicit object decoder extends Decoder[GoldenLayoutConfig] {
    override def apply(c: HCursor): Result[GoldenLayoutConfig] = {
      def nested: List[GoldenLayoutConfig] = c.downField("content").as[List[GoldenLayoutConfig]].getOrElse(Nil)
      c.downField("type").as[String] match {
        case Left(err)       => Left(err)
        case Right("row")    => Right(GoldenLayoutConfig.row(nested))
        case Right("column") => Right(GoldenLayoutConfig.column(nested))
        case Right("stack")  => Right(GoldenLayoutConfig.stack(nested))
        case Right("component") =>
          val componentName = c.downField("componentName").as[String].toOption.getOrElse("")
          val state: Json   = c.downField("componentState").as[Json].toOption.getOrElse(Json.Null)
          Right(GoldenLayoutConfig.component(componentName, state))
        case Right(other) =>
          val fail = DecodingFailure(s"Expected 'row', 'column', 'stack' or 'content' but got '$other'", c.history)
          Left(fail)
      }
    }
  }
}
