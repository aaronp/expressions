import sbt._

import java.nio.file.Path

object Build {

  val zioVersion = "1.0.7"
  val zio = List(
    "dev.zio" %% "zio-interop-cats" % "3.0.2.0",
    "dev.zio" %% "zio"              % zioVersion,
    "dev.zio" %% "zio-streams"      % zioVersion,
    "dev.zio" %% "zio-test"         % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt"     % zioVersion % "test"
  )

  val prometheus = List(
    "io.prometheus" % "simpleclient_pushgateway" % "0.10.0"
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

  val Http4sVersion = "0.21.22"

  val typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.4.1"

  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.3"
  val logback      = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val logging      = List(scalaLogging, logback, "org.slf4j" % "slf4j-api" % "1.7.30")

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.8" % "test"

  def franz: List[ModuleID] = {
    zio ++ logging ++ circe ++ prometheus ++
      Seq(
        "com.github.aaronp" %% "eie"                     % "1.0.0",
        "com.github.aaronp" %% "args4c"                  % "0.7.0",
        "com.github.aaronp" %% "dockerenv"               % "0.5.4",
        "dev.zio"           %% "zio-streams"             % zioVersion,
        "dev.zio"           %% "zio-kafka"               % "0.14.0",
        "io.confluent"      % "kafka-streams-avro-serde" % "6.1.1",
        typesafeConfig,
        scalaTest
      )
  }

  def jvmClient = circe ++ Seq(
    "com.softwaremill.sttp.client3" %% "core" % "3.1.0",
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
        "org.http4s"   %% "http4s-blaze-server"         % Http4sVersion,
        "org.http4s"   %% "http4s-blaze-client"         % Http4sVersion,
        "org.http4s"   %% "http4s-circe"                % Http4sVersion,
        "org.http4s"   %% "http4s-dsl"                  % Http4sVersion,
        scalaTest
      )
  }
}
