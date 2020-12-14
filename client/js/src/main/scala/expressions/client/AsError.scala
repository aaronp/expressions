package expressions.client

object AsError {

  def apply(response: String): String = {
    val errorMsg = io.circe.parser.parse(response).toTry.flatMap { json =>
      val fromResult = json.hcursor.downField("result").as[String].toTry
      def fromErr    = json.hcursor.downField("error").as[String].toTry
      fromResult.orElse(fromErr)
    }
    errorMsg.getOrElse(response)
  }
}
