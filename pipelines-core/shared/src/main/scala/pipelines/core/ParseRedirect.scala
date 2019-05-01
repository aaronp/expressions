package pipelines.core

object ParseRedirect {

  private val RedirectR = """.*?\?redirectTo=([^&]+).*?""".r
  def unapply(queryString : String): Option[String] = {
    queryString match {
      case RedirectR(path) => Option(path)
      case _ => None
    }
  }

}
