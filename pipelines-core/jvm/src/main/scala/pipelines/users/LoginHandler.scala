package pipelines.users

import java.lang.reflect.Constructor

import com.typesafe.config.Config
import pipelines.users.jwt.Claims

import scala.concurrent.Future

/**
  * A class which can return some user claims for a login request
  */
trait LoginHandler[F[_]] {

  /**
    * Handle a login
    *
    * @param request
    * @return
    */
  def login(request: LoginRequest): F[Option[Claims]]
}

object LoginHandler {

  private def usersConfig(rootConfig: Config) = rootConfig.getConfig("pipelines.users")

  def handlerClassName(rootConfig: Config): Class[LoginHandler[Future]] = {
    val c1assName = usersConfig(rootConfig).getString("loginHandler")
    Class.forName(c1assName).asInstanceOf[Class[LoginHandler[Future]]]
  }

  /**
    * Create a 'LoginHandler' from the given configuration. A class implementing 'LoginHandler' should either have a no-args
    * constructor OR a constructor which takes an instance of a typesafe Config, where the config is the 'pipelines.users' config
    *
    * @param rootConfig the root config
    * @return a new login handler
    */
  def apply(rootConfig: Config): LoginHandler[Future] = {
    val configConstructorArgs = List(classOf[Config])
    val c1ass                 = handlerClassName(rootConfig)
    c1ass.getConstructors.find(_.getParameterTypes.toList == configConstructorArgs) match {
      case Some(ctr: Constructor[_]) => ctr.asInstanceOf[Constructor[LoginHandler[Future]]].newInstance(usersConfig(rootConfig))
      case None                      => c1ass.newInstance()
    }

  }
}
