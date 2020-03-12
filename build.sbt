name := "WsFphp"

version := "1.0"

scalaVersion := "2.12.8"

lazy val Versions = new {
  val akka = "2.6.3"
  val akkaHttp  = "10.1.10"//"10.1.11"
  val circeVers = "0.12.3"
  val logbackVers = "1.2.3"
  val pgVers = "42.2.5"
  val zioVers = "1.0.0-RC18-2"
  //val zioVers = "0.2.0+3061-72c7c2a7-SNAPSHOT"
  val zioLog = "0.2.3"
  val zioLogSlf4j = "0.2.3"
  val dbcp2Vers = "2.7.0"
  val jschVers = "0.1.55"
  val zioPureConf = "0.12.2"
  val typeSefeConf = "1.4.0"
}

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  Resolver.bintrayRepo("websudos", "oss-releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("public")
)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

//paradise for using circe annotations, f.e. @JsonCodec case class DbErrorDesc
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % Versions.logbackVers,
  "com.typesafe" % "config" % Versions.typeSefeConf,
  "org.postgresql" % "postgresql" % Versions.pgVers,
  "dev.zio" %% "zio" % Versions.zioVers,
  "dev.zio" %% "zio-logging" % Versions.zioLog,
  "dev.zio" %% "zio-logging-slf4j" % Versions.zioLogSlf4j,
  "com.github.pureconfig" %% "pureconfig" % Versions.zioPureConf,
  "org.apache.commons" % "commons-dbcp2" % Versions.dbcp2Vers
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % Versions.akka,
  "com.typesafe.akka" %% "akka-stream" % Versions.akka,
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-slf4j" % Versions.akka
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-literal"
).map(_ % Versions.circeVers)

assemblyMergeStrategy in assembly := {
  case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly :="wsfphp.jar"
mainClass in (Compile, packageBin) := Some("application.Main")
mainClass in (Compile, run) := Some("application.Main")
/*

  "dev.zio" %% "zio-logging" % "0.2.3",
  "dev.zio" %% "zio-logging-slf4j" % "0.2.3",
*/
