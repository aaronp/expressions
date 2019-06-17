package pipelines.rest

import java.nio.file.Path

import args4c.ConfigApp
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import pipelines.Localhost
import pipelines.reactive.PipelineService
import pipelines.socket.SocketRoutesSettings
import pipelines.ssl.GenCerts.CertAuthSettings
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

  def run(rootConfig: Config): RunningServer = {
    val config             = ensureCerts(rootConfig)
    val settings: Settings = Settings(config)
    val service            = PipelineService()(settings.env.ioScheduler)
    run(settings, service)
  }

  def run(settings: Settings, service: PipelineService): RunningServer = {
    val sslConf: SSLConfig = SSLConfig(settings.rootConfig)
    val socketSettings     = SocketRoutesSettings(settings.rootConfig, settings.secureSettings, settings.env)
    RunningServer(settings, sslConf, socketSettings.routes)
  }

  def certPath(config: Config) = config.getString("pipelines.tls.certificate")
  def ensureCerts(config: Config): Config = {
    import eie.io._
    val crtPath = certPath(config)
    if (!crtPath.asPath.isFile && config.hasPath("generateMissingCerts")) {
      val (_, password) = ensureCert(config)
      config.set("pipelines.tls.password", password)
    } else {
      config
    }
  }

  /**
    * Create our own self-signed cert (if required) for local development
    */
  def ensureCert(config: Config): (Path, String) = {

    val password = Try(config.getString("pipelines.tls.password")).getOrElse("password")

    val hostName: String = Try(config.getString("pipelines.tls.hostname")).getOrElse(Localhost.hostAddress)

    val pathToCert = certPath(config)

    import eie.io._
    val certFile = pathToCert.asPath
    if (!certFile.isFile) {
      logger.info(s"${certFile} doesn't exist, creating it")
      val dir = Option(certFile.getParent).getOrElse(".".asPath)

      val settings = {
        val base = GenCerts.CertSettings(dir, certFile.fileName, hostName, password)

        //
        // if we've run the generator once before, we might want to capture the output, move it someplace stable,
        // override the specific options, and then re-run it to get a <file>-ca.crt result (set by CA_FILE)
        // which can then be used/specified by a
        //
        val authSettings: GenCerts.CertAuthSettings = {
          val caPathOpt: Option[Path] = Try(config.getString("pipelines.tls.gen.caCertPath").asPath).toOption.filter(_.isFile)
          Try(config.getString("pipelines.tls.gen.caDir").asPath).toOption.filter(_.isDir) match {
            case None => CertAuthSettings().copy(caFile = caPathOpt)
            case Some(caDir) =>
              CertAuthSettings.fromGeneratedCaDir(caDir).copy(caFile = caPathOpt)
          }
        }

        base.copy(authSettings = Option(authSettings))
      }

      val (resValue, buffer, certPath) = GenCerts.genCert(settings)
      logger.info(s"created ${certPath} for $hostName:\n\n${buffer.allOutput}\n\n")
      require(resValue == 0, s"Gen cert script exited w/ non-zero value $resValue")
    } else {
      logger.info("dev cert exists, cracking on...")
    }
    certFile -> password
  }
}
