package pipelines.client

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.raw.{HTMLAnchorElement, HTMLElement, HTMLLinkElement, KeyboardEvent}
import pipelines.core.{GenericMessageResult, ParseRedirect}
import pipelines.users.{CreateUserRequest, CreateUserResponse, LoginRequest, LoginResponse, UserReminderRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

/**
  * top-level functions for login/logout, create/confirm users, etc
  */
object SourceFunctions extends HtmlUtils {

  @JSExportTopLevel("sources")
  def sendUserReminder(userInputId: String): Future[GenericMessageResult] = {
    val user = valueOf(userInputId)
    PipelinesXhr.resetUser.resetUserEndpoint.apply(UserReminderRequest(user))
  }
}
