package pipelines.client.users

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.raw.{HTMLAnchorElement, HTMLElement, HTMLLinkElement, KeyboardEvent}
import pipelines.client.{AppState, Constants, HtmlUtils, PipelinesXhr}
import pipelines.core.{GenericMessageResult, ParseRedirect}
import pipelines.users.{CreateUserRequest, CreateUserResponse, LoginRequest, LoginResponse, UserReminderRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

/**
  * top-level functions for login/logout, create/confirm users, etc
  */
object UserFunctions extends HtmlUtils {

  /**
    * Clears the locally stored "jwtToken"
    */
  @JSExportTopLevel("logout")
  def logout(): Unit = {
    log("Logging out")
    dom.window.sessionStorage.clear()
  }

  @JSExportTopLevel("loginOnKeyUp")
  def loginOnKeyUp(userInputId: String, pwdInputId: String, messagesId: String, e: KeyboardEvent) = {
    if (e.keyCode == 13) {
      login(userInputId, pwdInputId, messagesId)
    }
  }
  @JSExportTopLevel("createUserOnKeyUp")
  def createUserOnKeyUp(userInputId: String, emailId: String, pwdInputId: String, messagesId: String, e: KeyboardEvent) = {
    if (e.keyCode == 13) {
      createUser(userInputId, emailId, pwdInputId, messagesId)
    }
  }

  @JSExportTopLevel("login")
  def login(userInputId: String, pwdInputId: String, messagesId: String) = {

    val user    = valueOf(userInputId)
    val pwd     = valueOf(pwdInputId)
    val request = LoginRequest(user, pwd)

    val redirectUrl = dom.window.document.location.search match {
      case ParseRedirect(url) => url
      case _                  => ""
    }

    val loginResponseFuture: Future[LoginResponse] = PipelinesXhr.userLogin.loginEndpoint.apply(request, Option(redirectUrl).filterNot(_.isEmpty))
    loginResponseFuture.onComplete {
      case Success(response: LoginResponse) if response.ok =>
        AppState.onLogin(response)
        redirectTo(Constants.pages.MainPage)
      case _ => // treat failure (exception) case the same as invalid logins
        setMessage(messagesId, "Invalid login")
    }
  }

  @JSExportTopLevel("userStatus")
  def userStatus(loginElmId: String, logoutElmId: String) = {

    def setVisible(id: String, visible: Boolean) = {
      elmById(id) match {
        case html: HTMLElement =>
          val value = if (visible) "inline" else "none"
          html.style.display = value
          Option(html)
        case _ => None
      }
    }
    def toggleOn(id: String, userName: String) = {
      if (id == loginElmId) {
        setVisible(loginElmId, true)
        setVisible(logoutElmId, false)
      } else {
        setVisible(loginElmId, false)
        setVisible(logoutElmId, true).foreach { html =>
          val ahrefOpt = childrenFor(html).collectFirst {
            case href: HTMLAnchorElement => href
            case href: HTMLLinkElement   => href
            case href: Node              => href
          }
          ahrefOpt.foreach { a =>
            a.textContent = s"${userName} logout"
            a.innerText = s"${userName} logout text"
          }
        }
      }
    }
    PipelinesXhr.userStatus.statusEndpoint.apply().onComplete {
      case Failure(err) =>
        log(s"User status requested failed with $err")
        toggleOn(loginElmId, "")
      case Success(status) =>
        log(s"User status is $status")
        toggleOn(logoutElmId, status.userName)
    }
  }

  @JSExportTopLevel("createUser")
  def createUser(userInputId: String, emailId: String, pwdId: String, messagesId: String): Unit = {
    val user    = valueOf(userInputId)
    val email   = valueOf(emailId)
    val pwd     = valueOf(pwdId)
    val request = CreateUserRequest(user, email, pwd)

    // nifty - shared validation! :-)
    request.validationErrors() match {
      case Seq() =>
        log(s"Creating : $request")
        val future: Future[CreateUserResponse] = PipelinesXhr.createUser.createUserEndpoint.apply(request)
        future.onComplete {
          case Success(result) =>
            result.error match {
              case Some(errMsg) =>
                setMessage(messagesId, errMsg)
              case None =>
                require(result.ok, "create user completed w/o errors but ok set to true")
                log(s"Create user returned: $result")
                setMessage(messagesId, s"Create user returned: $result")
                redirectTo(Constants.pages.MainPage)
            }
          case Failure(err) =>
            log(s"Create user returned: $err")
            setMessage(messagesId, s"Create user returned: $err")
        }
      case errors =>
        log(s"${errors.size} errors for $request ... ${errors.mkString(",")}")
        setMessage(messagesId, s"Computer says no: ${errors.mkString("<br/>")}")
    }
  }

  @JSExportTopLevel("sendUserReminder")
  def sendUserReminder(userInputId: String): Future[GenericMessageResult] = {
    val user = valueOf(userInputId)
    PipelinesXhr.resetUser.resetUserEndpoint.apply(UserReminderRequest(user))
  }
}
