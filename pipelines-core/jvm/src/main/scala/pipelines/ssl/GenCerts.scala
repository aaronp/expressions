package pipelines.ssl

import java.net.URL
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}
import java.util.zip.ZipInputStream

import com.typesafe.scalalogging.StrictLogging
import pipelines.Using

import scala.sys.process
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
}
