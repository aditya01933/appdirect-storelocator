import com.typesafe.sbt.packager.docker._
import com.github.play2war.plugin._

name := "appdirect-storelocator"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
//  jdbc,
//  anorm,
//  cache,
  ws,
  "com.typesafe.play" %% "play-slick" % "0.8.1",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4",
  "org.webjars" % "font-awesome" % "4.3.0-1"
)

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

// Do not include docs
doc in Compile <<= target.map(_ / "none")

// exposing the play ports
dockerExposedPorts in Docker := Seq(9000, 9443)

Play2WarPlugin.play2WarSettings

// Servlet version compatible with Tomcat 7
// See https://github.com/play2war/play2-war-plugin#server-compatibility
Play2WarKeys.servletVersion := "3.0"
