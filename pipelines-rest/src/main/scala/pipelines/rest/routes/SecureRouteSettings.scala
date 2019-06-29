package pipelines.rest.routes

import com.typesafe.config.Config
import javax.crypto.spec.SecretKeySpec
import pipelines.users.jwt.Hmac256

case class SecureRouteSettings(loginPage: String, realm: String, secret: SecretKeySpec)

object SecureRouteSettings {

  def fromRoot(config: Config): SecureRouteSettings = {
    fromConfig(config.getConfig("pipelines.www"))
  }

  def fromConfig(config: Config): SecureRouteSettings = {
    val nullableRealm = Option(config.getString("realmName")).filterNot(_.isEmpty).orNull
    apply(loginPage = config.getString("loginPage"), realmName = nullableRealm, secretSeed = config.getString("jwtSeed"))
  }
  def apply(loginPage: String, realmName: String, secretSeed: String): SecureRouteSettings = {
    new SecureRouteSettings(loginPage, realmName, Hmac256.asSecret(secretSeed))
  }
}
