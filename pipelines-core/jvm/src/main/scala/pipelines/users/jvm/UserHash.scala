package pipelines.users.jvm

import java.security.SecureRandom
import java.util.Base64

import com.typesafe.config.Config
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class UserHash private[jvm] (random: SecureRandom, salt: Array[Byte], iterationCount: Int, keyLen: Int) {

  private val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
  private val enc = Base64.getEncoder

  def apply(password: String): String = {
    val spec = new PBEKeySpec(password.toCharArray, salt, iterationCount, keyLen)
    enc.encodeToString(skf.generateSecret(spec).getEncoded)
  }
}

object UserHash {

  def apply(rootConfig: Config): UserHash = {
    val config = rootConfig.getConfig("pipelines.tls.userHash")
    apply(
      salt = config.getString("salt").getBytes("UTF-8"),
      iterationCount = config.getInt("iterationCount"),
      keyLen = config.getInt("keyLen")
    )
  }

  def apply(salt: Array[Byte], iterationCount: Int, keyLen: Int): UserHash = {
    val random = new SecureRandom(salt)
    new UserHash(random, salt.toList.toArray, iterationCount, keyLen)
  }
}
