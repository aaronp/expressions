import sbt._

import java.nio.file.Path

object Build {

  val zioVersion = "1.0.12"
//  val zioVersion = "2.0.0-M3"
  val zio = List(
    "dev.zio" %% "zio-interop-cats" % "2.5.1.0",
    "dev.zio" %% "zio"              % zioVersion,
    "dev.zio" %% "zio-streams"      % zioVersion,
    "dev.zio" %% "zio-test"         % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt"     % zioVersion % "test"
  )

  val prometheus = List(
    "io.prometheus" % "simpleclient_pushgateway" % "0.10.0"
  )

  val circeVersion          = "0.14.1"
  val circe = {
    List(
      "io.circe" %% "circe-generic"        % circeVersion,
//      "io.circe" %% "circe-generic-extras" % circeGenExtrasVersion,
      "io.circe" %% "circe-parser"         % circeVersion,
//      "io.circe" %% "circe-literal"        % circeVersion % Test
    )
  }

//  val Http4sVersion = "0.23.4"
  val Http4sVersion = "0.22.0-RC1"

  val typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.4.1"

  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
  val logback      = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val logging      = List(scalaLogging, logback, "org.slf4j" % "slf4j-api" % "1.7.32")

  val scalaTest =  List ("org.scalactic" %% "scalactic" % "3.2.10" % Test,
  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
  "org.pegdown" % "pegdown" % "1.6.0" % Test,
  "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
  "junit" % "junit" % "4.13" % Test)

  def franz: List[ModuleID] = {

    val explicitCats = List("cats-core", "cats-kernel").map { art =>
      ("org.typelevel" %%  art % "2.6.1").exclude("org.scala-lang", "scala3-library") //_3:3.0.1-RC1
    }

    val aaronp = List(
      "com.github.aaronp" %% "eie" % "1.0.0",
      "com.github.aaronp" %% "args4c" % "0.7.0",
      "com.github.aaronp" %% "dockerenv"               % "0.6.0"
    ).map { art =>
      art.cross(CrossVersion.for3Use2_13)
        .exclude("com.typesafe.scala-logging", "scala-logging")
        .exclude("com.typesafe.scala-logging", "scala-logging_2.13")
        .exclude("com.github.mpilquist", "simulacrum"),
    }

    zio ++ logging ++ circe ++ prometheus ++ aaronp ++ explicitCats ++ scalaTest ++
      Seq(
        "dev.zio"           %% "zio-streams"             % zioVersion,
        "dev.zio"           %% "zio-kafka"               % "0.17.1",
        "io.confluent"      % "kafka-streams-avro-serde" % "6.1.1",
        typesafeConfig,
      )
  }

  def jvmClient = circe ++ scalaTest ++ Seq(
    "com.softwaremill.sttp.client3" %% "core" % "3.3.14",
    "org.http4s" %% "http4s-blaze-client" % Http4sVersion
  )

  def rest: List[ModuleID] = {
    zio ++
      logging ++
      circe ++
      scalaTest ++
      Seq(
        ("com.github.aaronp" %% "eie"    % "1.0.0").cross(CrossVersion.for3Use2_13),
        ("com.github.aaronp" %% "args4c" % "0.7.0").cross(CrossVersion.for3Use2_13),
        typesafeConfig,
        "org.http4s"   %% "http4s-blaze-server"         % Http4sVersion,
        "org.http4s"   %% "http4s-blaze-client"         % Http4sVersion,
        "org.http4s"   %% "http4s-circe"                % Http4sVersion,
        "org.http4s"   %% "http4s-dsl"                  % Http4sVersion
      )
  }
}
