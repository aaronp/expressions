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

  def genCert(workDir: Path, certificateName: String, hostname: String, crtPassword: String, caPassword: String, jksPassword: String): (Int, BufferLogger, Path) = {
    val p12CertFile = workDir.resolve(certificateName)
    val (res, buffer) = run(
      "scripts/generateP12Cert.sh", //
      "CRT_DIR"           -> workDir.resolve("crt").toAbsolutePath.toString, //
      "CA_DIR"            -> workDir.resolve("ca").toAbsolutePath.toString, //
      "CRT_CERT_FILE_P12" -> p12CertFile.toAbsolutePath.toString, //
      "DNS_NAME"          -> hostname,
      "CRT_NAME"          -> hostname,
      "CRT_DEFAULT_PWD"   -> crtPassword,
      "CA_DEFAULT_PWD"    -> caPassword,
      "CRT_JKS_PW"        -> jksPassword
    )
    (res, buffer, p12CertFile)
  }

  def run(script: String, extraEnv: (String, String)*): (Int, BufferLogger) = {
    val buffer = new BufferLogger
    val res    = parseScript(script, extraEnv: _*).run(buffer)
    res.exitValue() -> buffer
  }

  private def parseScript(script: String, extraEnv: (String, String)*): process.ProcessBuilder = {
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
        val scriptPath = dir.resolve(s"scripts/$script")
        logger.info(s"Extracted $script to ${scriptPath.toAbsolutePath}")
        scriptPath
      case _ => Paths.get(location.toURI)
    }

    require(location != null, s"Couldn't find $script")
    import sys.process._

    val scriptFile = s"./${scriptLoc.getFileName.toString}"
    Process(scriptFile, scriptLoc.getParent.toFile, extraEnv: _*)
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
