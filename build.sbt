import java.nio.file.Path

val repo = "expressions"
name := repo

val username            = "aaronp"
val scalaThirteen       = "2.13.4"
val defaultScalaVersion = scalaThirteen
val scalaVersions       = Seq(defaultScalaVersion) //, scalaThirteen)

crossScalaVersions := scalaVersions
organization := s"com.github.${username}"
scalaVersion := defaultScalaVersion
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// see https://github.com/sbt/sbt-ghpages
// this exposes the 'ghpagesPushSite' task
enablePlugins(GhpagesPlugin)
enablePlugins(GitVersioning)
//enablePlugins(PamfletPlugin)
enablePlugins(SiteScaladocPlugin)

// see http://scalameta.org/scalafmt/
scalafmtOnCompile in ThisBuild := true
scalafmtVersion in ThisBuild := "1.4.0"

// Define a `Configuration` for each project, as per http://www.scala-sbt.org/sbt-site/api-documentation.html
val Expressions    = config("expressions")
val ExpressionsAst = config("expressionsAst")

git.remoteRepo := s"git@github.com:$username/$repo.git"
ghpagesNoJekyll := true

val circeVersion      = "0.13.0"
val circeDependencies = List("circe-core", "circe-generic", "circe-parser", "circe-generic-extras", "circe-optics")

val testDependencies = List(
  "junit"                  % "junit"      % "4.12"  % "test",
  "org.scalatest"          %% "scalatest" % "3.2.2" % "test",
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0" % "test",
  "org.pegdown"            % "pegdown"    % "1.6.0" % "test"
)

val simulacrum: ModuleID = "com.github.mpilquist" %% "simulacrum" % "0.13.0"

val Avro = "org.apache.avro" % "avro" % "1.10.1"

lazy val scaladocSiteProjects = List(
  (expressions, Expressions),
  (ExpressionsAst, ExpressionsAst)
)

lazy val scaladocSiteSettings = scaladocSiteProjects.flatMap {
  case (project: Project, conf) =>
    SiteScaladocPlugin.scaladocSettings(
      conf,
      mappings in (Compile, packageDoc) in project,
      s"api/${project.id}"
    )
  case _ => Nil // ignore cross-projects
}

lazy val settings = scalafmtSettings

def additionalScalcSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-unchecked",
  //  "-explaintypes", // Explain type errors in more detail.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xfuture", // Turn on future language features.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match", // Pattern match may not be typesafe.
  "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",     // Warn when nullary methods return Unit.
  //  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
)

val baseScalacSettings = List(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",
  "-language:reflectiveCalls", // Allow reflective calls
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  //"-Xlog-implicits",
  "-Xfuture" // Turn on future language features.
)

val scalacSettings = baseScalacSettings

val commonSettings: Seq[Def.Setting[_]] = Seq(
  //version := parentProject.settings.ver.value,
  organization := s"com.github.${username}",
  scalaVersion := defaultScalaVersion,
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  resolvers += "confluent" at "https://packages.confluent.io/maven/",
  autoAPIMappings := true,
  exportJars := false,
  crossScalaVersions := scalaVersions,
  javacOptions ++= Seq("-source", "1.10", "-target", "1.10"),
  scalacOptions ++= scalacSettings,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := s"${repo}.build",
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case str if str.contains("application.conf") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
  // see http://www.scalatest.org/user_guide/using_scalatest_with_sbt
  //(testOptions in Test) += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports-${name.value}", "-oN"))
)

test in assembly := {}

// don't publish the root artifact
publishArtifact := false

publishMavenStyle := true

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(
    expressions,
    expressionsAst,
    clientJS,
    clientJVM,
    rest,
    franz
  )
  .settings(scaladocSiteSettings)
  .settings(
    paradoxProperties += ("project.url" -> "https://aaronp.github.io/expressions/docs/current/"),
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    siteSourceDirectory := target.value / "paradox" / "site" / "main",
    siteSubdirName in ScalaUnidoc := "api/latest",
    publish := {},
    publishLocal := {}
  )

lazy val franz = project
  .in(file("franz"))
  .dependsOn(avroRecords % "test->compile")
  .settings(name := "franz")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Build.franz)

lazy val client = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .in(file("client"))
  .settings(commonSettings: _*)
  .settings(
    name := "client",
    //https://dzone.com/articles/5-useful-circe-feature-you-may-have-overlooked
    libraryDependencies ++= List(
      "io.circe" %%% "circe-generic"        % circeVersion,
      "io.circe" %%% "circe-generic-extras" % circeVersion,
      "io.circe" %%% "circe-parser"         % circeVersion,
      "io.circe" %%% "circe-literal"        % circeVersion % Test
    )
  )
  .jvmSettings(commonSettings: _*)
  .jvmSettings(
    name := "client-jvm",
    siteSubdirName in SiteScaladoc := "api/latest",
    libraryDependencies ++= Build.jvmClient
  )
  .jsSettings(scalaJSUseMainModuleInitializer in Global := true)
  .jsSettings(name := "client-js")
  .jsSettings(test := {}) // ignore JS tests - they're all done on the JVM
  .jsSettings(libraryDependencies ++= List(
    "org.scala-js"  %%% "scalajs-java-time" % "1.0.0",
    "com.lihaoyi"   %%% "scalatags"         % "0.9.2",
    "org.scala-js"  %%% "scalajs-dom"       % "1.1.0",
    "org.scalatest" %%% "scalatest"         % "3.1.2" % "test"
  ))

lazy val clientJVM = client.jvm
lazy val clientJS  = client.js

lazy val rest = (project in file("rest"))
  .settings(commonSettings: _*)
  .settings(
    name := "rest",
    libraryDependencies ++= Build.rest,
    mainClass := Some("expressions.rest.Main"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
  .settings(commonSettings: _*)
  .dependsOn(expressions % "compile->compile;test->test")
  .dependsOn(clientJVM % "compile->compile;test->test")
  .dependsOn(franz % "compile->compile;test->test")
  .dependsOn(avroRecords % "test->compile")

lazy val example = project
  .in(file("example"))
  .dependsOn(expressionsAst % "compile->compile;test->test")
  .dependsOn(avroRecords % "compile->compile;test->test")
  .settings(name := "example", coverageFailOnMinimum := false)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= testDependencies)

lazy val avroRecords = project
  .in(file("avro-records"))
  .settings(name := "avro-records", coverageFailOnMinimum := false)
  .settings(commonSettings: _*)
//  .settings((stringType in AvroConfig) := "String")
  .settings(libraryDependencies += Avro)
  .settings(libraryDependencies ++= testDependencies)

lazy val expressions = project
  .in(file("expressions"))
  .dependsOn(avroRecords % "test->compile")
  .settings(name := "expressions", coverageMinimum := 30, coverageFailOnMinimum := true)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= testDependencies)
  .settings(libraryDependencies ++= circeDependencies.map(artifact => "io.circe" %% artifact % circeVersion))
  .settings(libraryDependencies += "com.github.aaronp" %% "eie" % "1.0.0")
  .settings(libraryDependencies ++= List(
    "org.apache.avro" % "avro"           % "1.10.0",
    "org.scala-lang"  % "scala-reflect"  % "2.13.4",
    "org.scala-lang"  % "scala-compiler" % "2.13.4",
    "io.circe"        %% "circe-literal" % circeVersion % "test"
  ))

lazy val expressionsAst = project
  .in(file("expressions-ast"))
  .settings(name := "expressions-ast", coverageMinimum := 30, coverageFailOnMinimum := true)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= testDependencies)
  .settings(libraryDependencies ++= List("com.lihaoyi" %% "fastparse" % "2.3.0"))
  .dependsOn(expressions % "compile->compile;test->test")

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/${username}/${repo}
  </url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>${username}</id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/${username}/${repo}
        </url>
      </developer>
    </developers>
}
