package pipelines.ssl

import java.nio.file.Path

import com.typesafe.config.Config
import pipelines.Localhost
import pipelines.ssl.CertSetup.certPath

import scala.util.Try

case class CertSettings(certDir: Path,
                        caDir: Path,
                        p12CertFile: Path,
                        dnsName: String,
                        crtName: String,
                        certDefaultPassword: String,
                        certJksPassword: String,
                        crtDetails: Option[Path],
                        authSettings: Option[CertAuthSettings]) {
  def asPropertyMap(): Map[String, String] = {
    val default = Map(
      "CRT_DIR"           -> certDir.toAbsolutePath.toString, //
      "CA_DIR"            -> caDir.toAbsolutePath.toString, //
      "CRT_CERT_FILE_P12" -> p12CertFile.toAbsolutePath.toString, //
      "DNS_NAME"          -> dnsName,
      "CRT_NAME"          -> crtName,
      "CRT_DEFAULT_PWD"   -> certDefaultPassword,
      "CRT_JKS_PWD"       -> certJksPassword
    )

    val withCrtDetails = crtDetails.fold(default) { path =>
      default.updated("CRT_DETAILS_FILE", path.toAbsolutePath.toString)
    }
    authSettings.fold(withCrtDetails)(a => withCrtDetails ++ a.asPropertyMap())
  }
}

object CertSettings {

  def apply(config: Config): CertSettings = {
    val password = Try(config.getString("pipelines.tls.password")).getOrElse("password")

    val hostName: String = Try(config.getString("pipelines.tls.hostname")).getOrElse(Localhost.hostAddress)

    val pathToCert = certPath(config)

    import eie.io._
    val certFile = pathToCert.asPath

    val dir = Option(certFile.getParent).getOrElse(".".asPath)

    val base = CertSettings(dir, certFile.fileName, hostName, password)

    //
    // if we've run the generator once before, we might want to capture the output, move it someplace stable,
    // override the specific options, and then re-run it to get a <file>-ca.crt result (set by CA_FILE)
    // which can then be used/specified by a
    //
    val authSettings: CertAuthSettings = {
      val caPathOpt: Option[Path] = Try(config.getString("pipelines.tls.gen.caCertPath").asPath).toOption.filter(_.isFile)
      Try(config.getString("pipelines.tls.gen.caDir").asPath).toOption.filter(_.isDir) match {
        case None => CertAuthSettings(password).copy(caFile = caPathOpt)
        case Some(caDir) =>
          CertAuthSettings.fromGeneratedCaDir(caDir).copy(caFile = caPathOpt, caDefaultPassword = password)
      }
    }

    base.copy(authSettings = Option(authSettings))
  }

  def apply(workDir: Path, certificateName: String, hostname: String, password: String, authSettings: Option[CertAuthSettings] = None): CertSettings = {
    val p12CertFile: Path = workDir.resolve(certificateName)
    new CertSettings(
      certDir = workDir.resolve("crt"), //
      caDir = workDir.resolve("ca"), //
      p12CertFile = p12CertFile, //
      dnsName = hostname,
      crtName = hostname,
      certDefaultPassword = password,
      certJksPassword = password,
      None,
      authSettings
    )
  }
}
