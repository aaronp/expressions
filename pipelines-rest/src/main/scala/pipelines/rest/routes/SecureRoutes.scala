package pipelines.rest.routes

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives.extractRequest
import javax.crypto.spec.SecretKeySpec
import pipelines.users.Claims

class SecureRoutes(settings: SecureRouteSettings) extends AuthenticatedDirective {

  def loginPage                      = settings.loginPage
  override val realm: String         = settings.realm
  override val secret: SecretKeySpec = settings.secret

  def authWithQuery: Directive[(Claims, Uri.Query)] = {
    authenticated.tflatMap { user =>
      extractRequest.map { r =>
        val q: Uri.Query = r.uri.query()
        (user._1, q)
      }
    }
  }

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
