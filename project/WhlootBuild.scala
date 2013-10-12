import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object CsiBuild extends Build {

  EclipseKeys.skipParents in ThisBuild := false

  lazy val whlootService = Project(
    id = "whloot",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "Whloot",
      organization := "com.larskroll",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.0",
      //scalacOptions += "-Ydependent-method-types",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:postfixOps", "-language:implicitConversions"),
      resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      resolvers += "spray repo" at "http://repo.spray.io",
      resolvers += "spray nightly repo" at "http://nightlies.spray.io",
      resolvers += "sonatype releases"  at "https://oss.sonatype.org/content/repositories/releases/",
      resolvers += "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11.0-M3" % "2.2.0-RC1",
      libraryDependencies += "com.typesafe.akka" %   "akka-slf4j_2.11.0-M3" % "2.2.0-RC1",
      libraryDependencies += "com.typesafe.akka" %   "akka-testkit_2.11.0-M3" % "2.2.0-RC1",
      libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.2" % "test",
      libraryDependencies += "play" %% "anorm" % "2.1.5",
      libraryDependencies += "com.github.seratch" %% "scalikejdbc"               % "[1.6,)",
      libraryDependencies += "com.github.seratch" %% "scalikejdbc-interpolation" % "[1.6,)",
      libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.21",
      libraryDependencies += "org.slf4j" % "slf4j-simple" % "[1.7,)",
      //libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.4",
      libraryDependencies += "io.spray" % "spray-caching" % "1.2-M8",
      libraryDependencies += "io.spray" % "spray-routing" % "1.2-M8",
      libraryDependencies += "io.spray" % "spray-testkit" % "1.2-M8",
      libraryDependencies += "io.spray" % "spray-util" % "1.2-M8",
      libraryDependencies += "io.spray" %%  "spray-json" % "1.2.5"
      // libraryDependencies += "org.mindrot" % "jbcrypt" % "0.3m",
      // libraryDependencies += "com.oracle" % "ojdbc6" % "11.2.0.3",
      // libraryDependencies += "org.specs2" % "specs2_2.10.0-RC5" % "1.12.3",
      // libraryDependencies += "com.chuusai" %% "shapeless" % "1.2.3" cross CrossVersion.full
    ) ++ seq(Revolver.settings: _*)
  )
}
