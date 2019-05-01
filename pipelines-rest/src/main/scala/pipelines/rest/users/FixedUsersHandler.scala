package pipelines.rest.users

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import pipelines.rest.jwt.Claims
import pipelines.users.LoginRequest

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class FixedUsersHandler(userConfig: Config) extends LoginHandler with StrictLogging {
  import args4c.implicits._
  val sessionDuration = userConfig.getDuration("sessionDuration", TimeUnit.MILLISECONDS).millis
  val usersByName     = userConfig.getConfig("fixed").collectAsMap()
  override def login(request: LoginRequest): Future[Option[Claims]] = {
    val result: Try[Option[Claims]] = Try(usersByName.get(request.user).filter(_ == request.password).map { _ =>
      Claims.after(sessionDuration).forUser(request.user)
    })
    val success = result.toOption.flatten.exists(_.name == request.user)
    logger.info(s"Login from '${request.user}' ${if (success) "ok" else "failed"}")
    Future.fromTry(result)
  }
}
