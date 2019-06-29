package pipelines.mongo.users

import com.typesafe.config.Config
import monix.execution.Scheduler
import pipelines.users.jwt.Claims
import pipelines.users.{LoginHandler, LoginRequest}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class LoginHandlerMongo(val users: UserServiceMongo, val auth: AuthenticationService, sessionDuration: FiniteDuration) extends LoginHandler {

  override def login(request: LoginRequest): Future[Option[Claims]] = {
    implicit val ec = users.ioSched
    users.findUser(request.user).map {
      case Some(found) =>
        if (found.hashedPassword == users.hasher(request.password)) {
          val roles: Set[String] = auth.rolesForUser(found.userName)
          val perms: Set[String] = roles.flatMap(auth.permissionsForRole)
          val claims             = Claims.after(sessionDuration).forUser(found.id).setRoles(roles).setPermissions(perms).copy(email = found.email)
          Some(claims)
        } else {
          None
        }
      case None => None
    }
  }
}

object LoginHandlerMongo {
  def apply(rootConfig: Config)(implicit ioSched: Scheduler): Future[LoginHandlerMongo] = {
    import args4c.implicits._
    val sessionDuration                           = rootConfig.asFiniteDuration("pipelines.users.sessionDuration")
    val usersFuture: Future[UserServiceMongo]     = UserServiceMongo(rootConfig)
    val authFuture: Future[AuthenticationService] = AuthenticationService(rootConfig)
    for {
      u <- usersFuture
      a <- authFuture
    } yield {
      new LoginHandlerMongo(u, a, sessionDuration)
    }
  }
}
