package pipelines.ssl

import java.nio.file.Path

import args4c.implicits._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

object CertSetup extends StrictLogging {

  def certPath(config: Config) = config.getString("pipelines.tls.certificate")

  def ensureCerts(config: Config): Config = {
    import eie.io._
    val crtPath = certPath(config)
    if (!crtPath.asPath.isFile && config.hasPath("pipelines.generateMissingCerts")) {
      val (_, password) = ensureCert(config)
      config.set("pipelines.tls.password", password)
    } else {
      logger.info(s"Using cert from ${crtPath.asPath}")
      config
    }
  }

  /**
    * Create our own self-signed cert (if required) for local development
    */
  def ensureCert(config: Config): (Path, String) = {
    ensureCert(CertSettings(config))
  }

  def ensureCert(settings: CertSettings): (Path, String) = {
    import eie.io._

    if (!settings.p12CertFile.exists()) {
      logger.info(s"${settings.p12CertFile} doesn't exist, creating it")

      val (resValue, buffer, certPath) = GenCerts.genCert(settings)
      logger.info(s"created ${certPath} for ${settings.dnsName}:\n\n${buffer.allOutput}\n\n")
      require(resValue == 0, s"Gen cert script exited w/ non-zero value $resValue")
    } else {
      logger.info("dev cert exists, cracking on...")
    }
    settings.p12CertFile -> settings.certDefaultPassword
  }
}
