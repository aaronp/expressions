package pipelines.users.jvm

import org.scalatest.{Matchers, WordSpec}

class PasswordHashTest extends WordSpec with Matchers {

  "UserHash" should {
    "hash consistently" in {
      val hasher = PasswordHash("secret".getBytes("UTF-8"), 65536, 128)
      val hash   = hasher("P4ssw0rd")
      hash should not be "P4ssw0rd"
      hash should not be hasher("P4ssw0rd2")
      hash should not be hasher("P4ssw0rd".reverse)
    }
  }
}
