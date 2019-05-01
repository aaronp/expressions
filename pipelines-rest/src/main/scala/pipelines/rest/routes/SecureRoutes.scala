package pipelines.rest.routes

import akka.http.scaladsl.model.Uri
import javax.crypto.spec.SecretKeySpec

class SecureRoutes(settings: SecureRouteSettings) extends AuthenticatedDirective {

  def loginPage                      = settings.loginPage
  override val realm: String         = settings.realm
  override val secret: SecretKeySpec = settings.secret

  /**
    * @param intendedPath the original URI the user was trying to access
    * @return a login URI which may contain a 'redirectTo' query parameter which specifies this intendedPath
    */
  override def loginUri(intendedPath: Uri): Uri = {
    intendedPath.copy(rawQueryString = Option(s"intendedPath=${intendedPath.path.toString()}"), fragment = None, path = Uri.Path(loginPage))
  }
}

object SecureRoutes {
  def apply(settings: SecureRouteSettings): SecureRoutes = {
    new SecureRoutes(settings)
  }
}
