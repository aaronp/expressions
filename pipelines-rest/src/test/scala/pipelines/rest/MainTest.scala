package pipelines.rest

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import eie.io._

class MainTest extends WordSpec with Matchers {
  "Main.ensureCert" should {
    "create local certificates -- this test is also required for docker deploy to use in application.conf" in {
      val config = ConfigFactory.parseString("""pipelines.tls.password = kennwort
          |pipelines.tls.hostname = localhost
          |pipelines.tls.certificate = target/certificates/cert.p12
        """.stripMargin)

      val (file, "kennwort") = Main.ensureCert(config)
      try {
        file.isFile shouldBe true
      } finally {
        file.delete()
      }
    }
  }
}
