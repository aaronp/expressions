package pipelines.rest

import java.net.InetAddress
import java.nio.file.Path

import args4c.ConfigApp
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import pipelines.socket.SocketRoutesSettings
import pipelines.ssl.{GenCerts, SSLConfig}

import scala.util.Try

/**
  * The main entry point for the REST service
  *
  * (If you change/rename this, be sure to update pipelines-deploy/src/main/resources/boot.sh and project/Build.scala)
  *
  */
object Main extends ConfigApp with StrictLogging {
  type Result = RunningServer

  override protected val configKeyForRequiredEntries = "pipelines.requiredConfig"

  def run(config: Config): RunningServer = {
    import eie.io._
    val certPath = config.getString("pipelines.tls.certificate")
    val preparedConf = if (!certPath.asPath.isFile && config.hasPath("generateMissingCerts")) {
      val password = Try(config.getString("pipelines.tls.password")).getOrElse("password")
      ensureCert(certPath, password)
      config.set("pipelines.tls.password", password)
    } else {
      config
    }

    val sslConf: SSLConfig = SSLConfig(preparedConf)

    val settings       = Settings(preparedConf)
    val socketSettings = SocketRoutesSettings(settings.rootConfig, settings.secureSettings, settings.env)
    RunningServer(settings, sslConf, socketSettings.routes)
  }

  /**
    * Create our own self-signed cert (if required) for local development
    */
  def ensureCert(pathToCert: String, password: String = "password"): Path = {
    import eie.io._
    val certFile = pathToCert.asPath
    if (!certFile.isFile) {
      logger.info(s"${certFile} doesn't exist, creating it")
      val dir = Option(certFile.getParent).getOrElse(".".asPath)

//      val (resValue, buffer, certPath) = GenCerts.genCert(dir, certFile.fileName, "localhost", password, password, password)
      val localhost = InetAddress.getLocalHost.getHostAddress

      val (resValue, buffer, certPath) = GenCerts.genCert(dir, certFile.fileName, localhost, password, password, password)
      logger.info(s"created ${certPath} for $localhost:\n\n${buffer.allOutput}\n\n")
      require(resValue == 0, s"Gen cert script exited w/ non-zero value $resValue")
    } else {
      logger.info("dev cert exists, cracking on...")
    }
    certFile
  }
}
