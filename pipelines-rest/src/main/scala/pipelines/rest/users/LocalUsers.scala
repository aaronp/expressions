package pipelines.rest.users

import java.net.URLEncoder
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import pipelines.rest.jwt.Claims
import pipelines.users.LoginRequest

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * A basic user controller which just writes down users locally
  *
  * @param userDir
  */
class LocalUsers(userDir: Path, sessionDuration: FiniteDuration) extends LoginHandler {
  import eie.io._

  def this(userConfig: Config) = {
    this(LocalUsers.dirForRootConfig(userConfig), userConfig.getDuration("sessionDuration", TimeUnit.MILLISECONDS).millis)
  }

  lazy val emailDir    = userDir.resolve("byEmail")
  lazy val userNameDir = userDir.resolve("byUserName")

  override def login(request: LoginRequest): Future[Option[Claims]] = {
    Future.fromTry(Try {
      List(userNameDir, emailDir)
        .map { dir =>
          dir.resolve(LocalUsers.safe(request.user)).resolve("password")
        }
        .collectFirst {
          case pwdFile if pwdFile.isFile && pwdFile.text == request.password =>
            Claims.after(sessionDuration).forUser(request.user)
        }
    })
  }

}
object LocalUsers {
  def dirForRootConfig(userConfig: Config): Path = {
    import eie.io._
    val createIfNotExists = Try(userConfig.getBoolean("local.createIfNotExists")).getOrElse(false)
    val dir               = userConfig.getString("local.dir").asPath

    if (createIfNotExists) {
      dir.mkDirs()
    }
    dir
  }
  def safe(s: String) = URLEncoder.encode(s, "UTF-8")

}
