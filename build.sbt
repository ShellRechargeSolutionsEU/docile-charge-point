enablePlugins(OssLibPlugin)

scalaVersion := tnm.ScalaVersion.prev

name := "docile-charge-point"

organization := "de.reinier"

mainClass := Some("chargepoint.docile.Main")

assemblyJarName in assembly := "docile.jar"

connectInput in run := true

libraryDependencies ++= Seq(
  "com.lihaoyi"            % "ammonite"         % "1.0.3"    cross CrossVersion.full,
  "com.thenewmotion.ocpp" %% "ocpp-j-api"       % "6.0.3",
  "org.rogach"            %% "scallop"          % "3.1.1",
  "org.scala-lang"         % "scala-compiler"   % "2.11.11",

  "biz.enef"              %% "slogging"         % "0.6.1",
  "biz.enef"              %% "slogging-slf4j"   % "0.6.1",
  "org.slf4j"              % "slf4j-api"        % "1.7.25",
  "ch.qos.logback"         % "logback-classic"  % "1.2.3",

  "org.specs2"            %% "specs2-core"      % "4.0.2"    % "test"
)
