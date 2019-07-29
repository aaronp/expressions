package pipelines.ssl

import eie.io._
import org.scalatest.{Matchers, WordSpec}
import pipelines.WithTempDir

class GenCertsTest extends WordSpec with Matchers {

  "GenCerts" should {
    "create a certificate" in {
      WithTempDir { dir =>
        val testHostName = "testHostName"

        val pwd                  = "password"
        val (res, log, certFile) = GenCerts.genCert(dir, "cert.p12", testHostName, pwd)
        res shouldBe 0
        withClue(log.allOutput) {
          certFile.size.toInt should be > 0
          log.allOutput should include(certFile.fileName)
        }

      }
    }
  }
}
