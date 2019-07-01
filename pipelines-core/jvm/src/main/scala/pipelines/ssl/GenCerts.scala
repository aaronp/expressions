package pipelines.ssl

import java.net.URL
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}
import java.util.zip.ZipInputStream

import com.typesafe.scalalogging.StrictLogging
import pipelines.Using

import scala.collection.mutable.ArrayBuffer
import scala.sys.process
import scala.sys.process.ProcessLogger
import scala.util.Properties

/**
  * Utility for creating a certificate on a host. This is in 'main' (rather than just test) code, as we could potentially use this for other envs (staging, uat, etc)
  */
object GenCerts extends StrictLogging {

  def genCert(workDir: Path, certificateName: String, hostname: String, password: String): (Int, BufferLogger, Path) = {
    val properties: CertSettings = CertSettings(workDir, certificateName, hostname, password)
    genCert(properties)
  }

  def genCert(properties: CertSettings): (Int, BufferLogger, Path) = {
    genCert(properties.p12CertFile, properties.asPropertyMap())
  }

  def genCert(p12CertFile: Path, envProps: Map[String, String]): (Int, BufferLogger, Path) = {
    val (res, buffer) = run("scripts/generateP12Cert.sh", envProps)
    (res, buffer, p12CertFile)
  }

  case class CertAuthSettings(caDefaultPassword: String, caDetails: Option[Path], caFile: Option[Path], caPasswordFile: Option[Path], privateKeyFile: Option[Path]) {
    def asPropertyMap(): Map[String, String] = {
      def empty = Map.empty[String, String]

      val caDetailsMap = caDetails.fold(empty) { path =>
        Map("CA_DETAILS_FILE" -> path.toAbsolutePath.toString)
      }

      val caPasswordFileMap = caPasswordFile.fold(empty) { path =>
        Map("CA_PWFILE" -> path.toAbsolutePath.toString)
      }

      val caFileMap = caFile.fold(empty) { path =>
        Map("CA_FILE" -> path.toAbsolutePath.toString)
      }

      val privateKeyFileMap = privateKeyFile.fold(empty) { path =>
        Map("CA_PRIVATE_KEY_FILE" -> path.toAbsolutePath.toString)
      }

      caDetailsMap ++ caPasswordFileMap ++ caFileMap ++ privateKeyFileMap ++ Map("CA_DEFAULT_PWD" -> caDefaultPassword)
    }
  }

  object CertAuthSettings {
    def apply(pwd: String = "password"): CertAuthSettings = {
      new CertAuthSettings(pwd, None, None, None, None)
    }

    /**
      * If we just run the generator scripts once, we'll end up with a <dir>/ca/... file where the 'ca' directory contains
      *
      * @param caDir the presumably pre-generated directory containing the CA files such as:
      * {{{
      * <hostname>-ca.crt
      * ca-options.conf
      * capass.txt
      * secret.key
      * secret.pub
      * }}}
      * @return the CertAuthSettings from the files in the 'ca' directory
      */
    def fromGeneratedCaDir(caDir: Path): CertAuthSettings = {
      import eie.io._
      new CertAuthSettings(
        "password",
        caDetails = caDir.find(_.fileName == "ca-options.conf").toIterable.headOption,
        caFile = caDir.find(_.fileName.endsWith("-ca.crt")).toIterable.headOption,
        caPasswordFile = caDir.find(_.fileName == "capass.txt").toIterable.headOption,
        privateKeyFile = caDir.find(_.fileName == "secret.key").toIterable.headOption
      )
    }
  }
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
        "CRT_JKS_PW"        -> certJksPassword
      )

      val withCrtDetails = crtDetails.fold(default) { path =>
        default.updated("CRT_DETAILS_FILE", path.toAbsolutePath.toString)
      }
      authSettings.fold(withCrtDetails)(a => withCrtDetails ++ a.asPropertyMap())
    }
  }

  object CertSettings {
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

  def run(script: String, extraEnv: Map[String, String]): (Int, BufferLogger) = {
    val buffer = new BufferLogger
    val res    = parseScript(script, extraEnv).run(buffer)
    res.exitValue() -> buffer
  }

  def run(script: String, extraEnv: (String, String)*): (Int, BufferLogger) = run(script, extraEnv.toMap)

  private def parseScript(script: String, extraEnv: Map[String, String]): process.ProcessBuilder = {
    val location: URL = getClass.getClassLoader.getResource(script) match {
      case null => getClass.getProtectionDomain.getCodeSource.getLocation
      case url  => url
    }
    require(location != null, s"Couldn't find $script on the classpath")
    logger.info(s"Resolved $script to be $location, protocol '${location.getProtocol}'")

    val JarPath = ("jar:file:(.*)!/.*").r
    val scriptLoc: Path = location.toString match {
      case JarPath(pathToJar) =>
        import eie.io._
        val extractDir = Properties.userDir.asPath
        val dir        = extractScriptsFromJar(pathToJar, extractDir)
//        val scriptPath = dir.resolve(s"scripts/$script")
        val scriptPath = dir.resolve(script)
        logger.info(s"Extracted $script to ${scriptPath.toAbsolutePath}: ${scriptPath.toAbsolutePath.renderTree()}}")
        scriptPath
      case _ => Paths.get(location.toURI)
    }

    require(location != null, s"Couldn't find $script")
    import sys.process._

    val scriptFile = s"./${scriptLoc.getFileName.toString}"
    Process(scriptFile, scriptLoc.getParent.toFile, extraEnv.toSeq: _*)
  }

  private def extractScriptsFromJar(fromPath: String, toDir: Path): Path = {
    val fromFile = Paths.get(fromPath)

    import eie.io._

    val jarDest = toDir.mkDirs().resolve(fromFile.getFileName.toString)
    try {
      Files.copy(fromFile, toDir.resolve(fromFile.getFileName.toString))
    } catch {
      case _: FileAlreadyExistsException =>
    }

    Using(new ZipInputStream(Files.newInputStream(jarDest))) { is: ZipInputStream =>
      var entry = is.getNextEntry
      while (entry != null) {
        if (entry.getName.contains("scripts") && !entry.isDirectory) {
          val target = toDir.resolve(entry.getName)
          if (!Files.exists(target)) {
            target.mkParentDirs()
            Files.copy(is, target)

            // our noddy scripts are ok for 777
            import scala.collection.JavaConverters._
            Files.setPosixFilePermissions(target, PosixFilePermission.values().toSet.asJava)
          }
        }
        is.closeEntry
        entry = is.getNextEntry
      }
    }
    toDir
  }

  class BufferLogger extends ProcessLogger {
    private val outputBuffer = ArrayBuffer[String]()
    private val errorBuffer  = ArrayBuffer[String]()
    private val bothBuffer   = ArrayBuffer[String]()

    def allOutput   = bothBuffer.mkString("\n")
    def stdOutput   = outputBuffer.mkString("\n")
    def errorOutput = errorBuffer.mkString("\n")

    override def out(s: => String): Unit = {
      outputBuffer.append(s)
      bothBuffer.append(s)
    }

    override def err(s: => String): Unit = {
      errorBuffer.append(s)
      bothBuffer.append(s)
    }

    override def buffer[T](f: => T): T = f
  }
}
