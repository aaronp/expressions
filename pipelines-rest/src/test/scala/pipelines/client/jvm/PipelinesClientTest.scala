package pipelines.client.jvm

import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}

import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import eie.io._
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import pipelines.rest.routes.TraceRoute
import pipelines.rest.{RunningServer, RestSettings}
import pipelines.ssl.{CertSetup, GenCerts, SSLConfig}
import pipelines.users.FixedUsersHandler
import pipelines.{BaseCoreTest, Env, Using}

import scala.concurrent.duration._
import scala.util.Success

class PipelinesClientTest extends BaseCoreTest {

  def testConf(): Config = {
    val certPath = s"./target/certificates/PipelinesClient${System.currentTimeMillis}/certPath.p12".asPath.toAbsolutePath
    val caPath   = certPath.getParent.resolve("caDir")

    ConfigFactory.parseString(s"""pipelines.tls.password = special
                                              |pipelines.tls.hostname = localhost
                                              |pipelines.tls.seed = foo
                                              |pipelines.tls.certificate = ${certPath}
                                              |pipelines.tls.gen.caDir = ${caPath.toAbsolutePath}
                                             """.stripMargin).withFallback(ConfigFactory.load())

  }

  "PipelinesClient" should {
    "be able to connect over https when using the same certificate as the server" in {
      val config = testConf()

      Using(Env()) { implicit clientEnv =>
        Given("Some SSLConfig")
        val (pathToCert, pwd) = CertSetup.ensureCert(config)

        val settings           = RestSettings(config)
        val sslConf: SSLConfig = SSLConfig(config)

        And("A server running with that config with a login route")
        val loginHandler: FixedUsersHandler = FixedUsersHandler("testUser" -> "the password")
        val loginRoutes: Route              = settings.loginRoutes(loginHandler)(clientEnv.ioScheduler).routes
        Using(RunningServer.start(settings, sslConf, "doesn't matter", loginRoutes)) { _ =>
          When("We connect a client which uses the sslConf")
          val Success(client) = PipelinesClient(config)(clientEnv.ioScheduler)

          Then("We should be able to hit the login route without getting a 401")
          val result = client.login("testUser", "the password").futureValue
          result.jwtToken.isDefined shouldBe true
        }
      }
    }

    "be able to connect over plaintext" in {
      val config = testConf()

      Using(Env()) { implicit clientEnv =>
        Given("Some SSLConfig and generated certs")

        And("A server running with that config with a login route")
        val loginHandler = FixedUsersHandler("testUser" -> "the password")
        val settings     = RestSettings(config, clientEnv)
        val loginRoutes: Route = {
          val base = settings.loginRoutes(loginHandler)(clientEnv.ioScheduler).routes
          TraceRoute.log.wrap(base)
        }

        val ctxt: SSLContext = {
          import eie.io._

          val crtFile = {
            val dir                  = "./target/PipelinesClientTest456/".asPath.mkDirs()
            val (0, log, cacertPath) = GenCerts.genCert(dir, "testcert.p12", "localhost", "atlanta")
            println(log.allOutput)
            cacertPath.getParent.resolve("crt/localhost.crt")
          }

          val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

          val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

          val keyStor = KeyStore.getInstance(KeyStore.getDefaultType())
          keyStor.load(null)

          val cacert: Certificate = certFactory.generateCertificate(crtFile.inputStream())
          val x509                = cacert.asInstanceOf[X509Certificate]
          x509.checkValidity()

          keyStor.setCertificateEntry("caCert", cacert)
          tmf.init(keyStor)

          val sslCtxt = SSLContext.getInstance("TLS")

          sslCtxt.init(null, tmf.getTrustManagers(), null)
          sslCtxt
        }

        val https = ConnectionContext.https(ctxt)

        Using(RunningServer(settings, None, "your service goes here", loginRoutes)) { runningServer =>
          When("We connect a client which uses the sslConf")
          val url = PipelinesClient.hostPort(config)

          val client = PipelinesClient.forHost(s"http://${url}", None)(clientEnv.ioScheduler)

          Then("We should be able to hit the login route without getting a 401")
          val result = client.login("testUser", "the password").futureValue
          result.jwtToken.isDefined shouldBe true
        }
      }
    }
  }

  override def testTimeout: FiniteDuration = 90.seconds
}
