import java.nio.file.Path

import eie.io._
import sbt._

import scala.sys.process._

object Build {

  val zioVersion = "1.0.1"
  val zio = List(
    "dev.zio" %% "zio-interop-cats" % "2.2.0.0",
    "dev.zio" %% "zio"              % zioVersion,
    "dev.zio" %% "zio-streams"      % zioVersion,
    "dev.zio" %% "zio-test"         % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt"     % zioVersion % "test"
  )

  val circeVersion          = "0.13.0"
  val circeGenExtrasVersion = "0.13.0"
  val circe = {
    List(
      "io.circe" %% "circe-generic"        % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeGenExtrasVersion,
      "io.circe" %% "circe-parser"         % circeVersion,
      "io.circe" %% "circe-literal"        % circeVersion % Test
    )
  }

  val Http4sVersion = "0.21.7"

  val typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.4.0"

  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  val logback      = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val logging      = List(scalaLogging, logback, "org.slf4j" % "slf4j-api" % "1.7.30")

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2" % "test"

  def franz: List[ModuleID] = {
    zio ++ logging ++ circe ++
      Seq(
        "com.github.aaronp" %% "eie"                     % "1.0.0",
        "com.github.aaronp" %% "args4c"                  % "0.7.0",
        "com.github.aaronp" %% "dockerenv"               % "0.5.4",
        "dev.zio"           %% "zio-streams"             % "1.0.3",
        "dev.zio"           %% "zio-kafka"               % "0.13.0",
        "io.confluent"      % "kafka-streams-avro-serde" % "6.0.0",
        typesafeConfig,
        scalaTest
      )
  }

  def jvmClient = circe ++ Seq(
    "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
    scalaTest
  )

  def rest: List[ModuleID] = {
    zio ++
      logging ++
      circe ++
      Seq(
        "com.github.aaronp" %% "eie"    % "1.0.0",
        "com.github.aaronp" %% "args4c" % "0.7.0",
        typesafeConfig,
        "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
        "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
        "org.http4s" %% "http4s-circe"        % Http4sVersion,
        "org.http4s" %% "http4s-dsl"          % Http4sVersion,
        scalaTest
      )
  }

  def docker(deployResourceDir: Path, //
             scriptDir: Path,         //
             jsArtifacts: Seq[Path],  //
             webResourceDir: Path,    //
             restAssembly: Path,      //
             targetDir: Path,         //
             logger: sbt.util.Logger) = {

    logger.info(s""" Building Docker Image with:
         |
         |   deployResourceDir = ${deployResourceDir.toAbsolutePath}
         |   scriptDir         = ${scriptDir.toAbsolutePath}
         |   jsArtifacts       = ${jsArtifacts.map(_.toAbsolutePath).mkString(",")}
         |   webResourceDir    = ${webResourceDir.toAbsolutePath}
         |   restAssembly      = ${restAssembly.toAbsolutePath}
         |   targetDir         = ${targetDir.toAbsolutePath}
         |
       """.stripMargin)

    val pipelinesJsDir = targetDir.resolve("web/js").mkDirs()
    IO.copyDirectory(deployResourceDir.toFile, targetDir.toFile)
    if (scriptDir.exists()) {
      IO.copyDirectory(scriptDir.toFile, targetDir.resolve("scripts").toFile)
    }
    IO.copyDirectory(webResourceDir.toFile, targetDir.resolve("web").toFile)
    IO.copy(List(restAssembly.toFile -> (targetDir.resolve("app.jar").toFile)))
    //IO.copy(List("target/certificates/cert.p12".asPath.toFile -> (targetDir.resolve("localcert.p12").toFile)))
    IO.copy(jsArtifacts.map(jsFile => jsFile.toFile -> (pipelinesJsDir.resolve(jsFile.fileName).toFile)))

    execIn(targetDir, "docker", "build", "--tag=expressions", ".")
  }

  def execIn(inDir: Path, cmd: String*): Unit = {
    import scala.sys.process._
    val p: ProcessBuilder = Process(cmd.toSeq, inDir.toFile)
    val retVal            = p.!
    require(retVal == 0, cmd.mkString("", " ", s" in dir ${inDir} returned $retVal"))
  }

}
