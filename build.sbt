name := """election-bot"""
organization := "com.example"

version := "1.0-SNAPSHOT"

resolvers += Resolver.sonatypeRepo("releases")

lazy val root = (project in file(".")).enablePlugins(PlayScala, AssemblyPlugin)

scalaVersion := "2.13.12"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test

libraryDependencies ++= Seq(
  jdbc,
  "com.mysql" % "mysql-connector-j" % "8.0.33"
)

libraryDependencies += ws
libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.7"
libraryDependencies += "com.github.bellam" %% "oauth-signature" % "0.1.1"



// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyJarName in assembly := "election-bot.jar"
