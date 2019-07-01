package pipelines.client

import org.scalajs.dom
import pipelines.users.LoginResponse

case class AppState(loginResponseOpt: Option[LoginResponse] = None) {

  def currentToken(): Option[String] = {
    val token = dom.window.sessionStorage.getItem("jwtToken")
    Option(token).filterNot(_.isEmpty).orElse {
      loginResponseOpt.flatMap(_.jwtToken)
    }
  }

  def withResponse(loginResponse: LoginResponse) = {

    loginResponse.jwtToken.foreach { token: String =>
      dom.window.sessionStorage.setItem("jwtToken", token)
    }
    copy(loginResponseOpt = Option(loginResponse))
  }
}
