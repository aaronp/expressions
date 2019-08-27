package pipelines.ssl

import java.io.InputStream
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.{KeyStore, SecureRandom}

import cats.effect.IO
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.util.Try

/**
  *
  * @see see https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html for keystore types
  * @param pathToCertificate the path to the keystore file
  * @param keystoreType the keystore type (e.g. pkcs12, jks, etc)
  * @param keystorePw the keystore pw
  * @param serverTlsSeed the keystore pw
  */
class SSLConfig private (val pathToCertificate: Path, val keystoreType: String, val keystorePw: String, serverTlsSeed: String, otherEntries: Seq[Path]) extends StrictLogging {

  lazy val wrapper                  = SSLWrapper(keystoreType, keystorePw, serverTlsSeed, pathToCertificate +: otherEntries)
  def newContext(): Try[SSLContext] = Try(wrapper.ctxt)

}

object SSLConfig {
  import eie.io._

  def apply(rootConfig: Config): SSLConfig = fromConfig(rootConfig.getConfig("pipelines.tls"))

  def fromConfig(config: Config): SSLConfig = {
    apply(
      certPathOpt(config).getOrElse(throw new IllegalStateException(s"'pipelines.tls.certificate' set to '${certPathString(config)}' does not exist")),
      config.getString("password"),
      tlsSeedOpt(config).getOrElse(throw new IllegalStateException(s"'pipelines.tls.seed' not set"))
    )
  }

  def sslContext(password: Array[Char], seed: Array[Byte], keystore: InputStream, ksType: String, additionalEntries: Seq[(String, IO[InputStream])]): SSLContext = {
    sslContext(password, new SecureRandom(seed), keystore, ksType, additionalEntries)
  }

  def sslContext(password: Array[Char], random: SecureRandom, keystore: InputStream, keyStoreType: String, additionalEntries: Seq[(String, IO[InputStream])]): SSLContext = {
    val ks: KeyStore = KeyStore.getInstance(keyStoreType)
    ks.load(keystore, password)

    additionalEntries.foreach {
      case (name, is) =>
        val cf   = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(is.unsafeRunSync())
        ks.setCertificateEntry(name, cert)
    }

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val algo = TrustManagerFactory.getDefaultAlgorithm
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ks)

    val ctxt: SSLContext = SSLContext.getInstance("TLS")
    ctxt.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, random)
    ctxt
  }

  def tlsSeed(config: Config)                   = config.getString("seed")
  def tlsSeedOpt(config: Config)                = Option(tlsSeed(config)).filterNot(_.isEmpty)
  def certPathString(config: Config)            = config.getString("certificate")
  def certPathOpt(config: Config): Option[Path] = Option(certPathString(config)).map(_.asPath).filter(_.isFile)

  def apply(pathToCertificate: Path, keystorePW: String, serverTlsSeed: String, others: Seq[Path] = Nil): SSLConfig = {
    val ksType = pathToCertificate.fileName match {
      case P12() => "pkcs12"
      case JKS() => "jks"
      case other => sys.error(s"Unable to determine the key store type from ${other}")
    }
    apply(pathToCertificate, ksType, keystorePW, serverTlsSeed, others)
  }

  def apply(pathToCertificate: Path, keystoreType: String, pw: String, serverTlsSeed: String, others: Seq[Path]): SSLConfig = {
    new SSLConfig(pathToCertificate, keystoreType, pw, serverTlsSeed, others)
  }

  private object JKS {
    def unapply(path: String): Boolean = path.toLowerCase.endsWith(".jks")
  }
  private object P12 {

    def unapply(path: String): Boolean = {
      val lc = path.toLowerCase
      lc.endsWith(".p12") || lc.endsWith(".pfx")
    }
  }

}
