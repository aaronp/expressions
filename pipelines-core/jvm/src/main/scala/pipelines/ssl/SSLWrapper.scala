package pipelines.ssl

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.cert.Certificate
import java.security.{KeyStore, SecureRandom}

import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

private[ssl] case class SSLWrapper(val keystoreType: String, val keystorePw: String, serverTlsSeed: String, additionalEntries: Seq[Path]) extends StrictLogging {

  val ksPassword: Array[Char] = keystorePw.toCharArray()

  logger.info(s"Reading $keystoreType certificate from ${additionalEntries.mkString(",")} with password '${ksPassword.mkString("")}', seed '$serverTlsSeed'")

  val seedArray = serverTlsSeed.getBytes(StandardCharsets.UTF_8)

  val random = new SecureRandom(seedArray)

  val ks: KeyStore = KeyStore.getInstance(keystoreType)

  val certs: Seq[Certificate] = additionalEntries.map { path =>
    import eie.io._

    import scala.collection.JavaConverters._
    ks.load(path.inputStream(), ksPassword)
    val aliases: Set[String] = ks.aliases().asScala.toSet
    val theCert: Certificate = ks.getCertificate(aliases.head)
    theCert
  }

  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, keystorePw.toCharArray)

  val algo = TrustManagerFactory.getDefaultAlgorithm
  val tmf  = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
  tmf.init(ks)

  val ctxt: SSLContext = {
    val tls = SSLContext.getInstance("TLS")
    tls.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, random)
    tls
  }
}
