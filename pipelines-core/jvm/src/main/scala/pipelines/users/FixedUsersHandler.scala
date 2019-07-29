package pipelines.users

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * A login handler which takes the users/passwords from a fixed set based on the configuration
  *
  * @param userConfig
  */
class FixedUsersHandler(userConfig: Config) extends LoginHandler[Future] with StrictLogging {
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

object FixedUsersHandler {
  def apply(firstUserAndCreds: (String, String), usersAndCreds: (String, String)*): FixedUsersHandler = {
    val configs = (firstUserAndCreds +: usersAndCreds).map {
      case (user, pwd) =>
        val map = Map(s"fixed.${user}" -> pwd)
        import scala.collection.JavaConverters._
        ConfigFactory.parseMap(map.asJava)
    }

    val userConf = configs.reduce(_ withFallback _)

    val all = userConf.withFallback(ConfigFactory.parseString("sessionDuration : 5m"))
    new FixedUsersHandler(all)
  }
}
