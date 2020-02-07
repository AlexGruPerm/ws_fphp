name := "WsFphp"

version := "1.0"

scalaVersion := "2.12.8"

lazy val Versions = new {
  val akka = "2.6.1"
  val akkaHttp  = "10.1.11"
  val circeVers = "0.12.3"
  val logbackVers = "1.2.3"
  val pgVers = "42.2.5"
  val zioVers = "1.0.0-RC17"
  val zioLog = "0.2.0"
  val dbcp2Vers = "2.7.0"
  val jschVers = "0.1.55"
  val zioPureConf = "0.12.2"
  val typeSefeConf = "1.4.0"
}

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  Resolver.bintrayRepo("websudos", "oss-releases")
)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % Versions.logbackVers,
  "com.typesafe" % "config" % Versions.typeSefeConf,
  "org.postgresql" % "postgresql" % Versions.pgVers,
  "dev.zio" %% "zio" % Versions.zioVers,
  "dev.zio" %% "zio-logging" % Versions.zioLog,
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

