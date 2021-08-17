lazy val commonSettings = Seq(
  organization := "com.newmotion",
  scalaVersion := "2.12.11",
  crossScalaVersions := Seq("2.12.11"),
  scalacOptions ++= Seq("-Xlint:-nullary-unit"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")
)

lazy val commandLine = (project in file("cmd"))
  .dependsOn(core)
  .dependsOn(loader)
  .settings(
    commonSettings,
    name := "docile-charge-point-command-line",
    libraryDependencies ++= commandLineDeps,
    mainClass := Some("chargepoint.docile.Main"),
    assemblyJarName in assembly := "docile.jar",
    assemblyMergeStrategy in assembly := {
        case "reflect.properties" =>
            MergeStrategy.concat
	case x =>
	    val oldStrategy = (assemblyMergeStrategy in assembly).value
	    oldStrategy(x)
    },
    connectInput in run := true
  )

lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "docile-charge-point",
    libraryDependencies ++= coreDeps
  )

lazy val loader = (project in file("loader"))
  .dependsOn(core)
  .settings(
    commonSettings,
    libraryDependencies ++= loaderDeps(scalaVersion.value),
    name := "docile-charge-point-loader"
  )

lazy val lambda = (project in file("aws-lambda"))
  .dependsOn(core)
  .dependsOn(loader)
  .settings(
    commonSettings,
    name := "docile-charge-point-lambda",
    retrieveManaged := true,
    libraryDependencies ++= awsDeps,
    mainClass := Some("chargepoint.docile.Lambda"),
    assemblyJarName in assembly := "docile-lambda.jar"
  )

lazy val coreDeps = Seq(
  "com.thenewmotion.ocpp"       %% "ocpp-j-api"       % "9.2.3", //you need to compile this locally first
  "com.typesafe.scala-logging"  %% "scala-logging"    % "3.9.0",

  "org.specs2"                  %% "specs2-core"      % "4.3.4"    % "test"
)

def loaderDeps(scalaVersion: String) = Seq(
  "org.scala-lang"               % "scala-compiler"   % scalaVersion,
)

lazy val commandLineDeps = Seq(
  "com.lihaoyi"                  % "ammonite"         % "2.1.4"    cross CrossVersion.full,
  "org.rogach"                  %% "scallop"          % "3.1.3",
  "ch.qos.logback"               % "logback-classic"  % "1.2.3"
)

lazy val awsDeps = Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.337",
  "com.amazonaws" % "aws-java-sdk-lambda" % "1.11.337",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-events" % "1.2.0"
)

commonSettings

name := "docile-charge-point-root"
