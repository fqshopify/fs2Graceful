ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "fs2Graceful"
  )

val fs2 = "3.2.8"
val fs2Kafka = "2.4.0"
val munitCatsEffect = "1.0.0"
val embeddedKafka = "2.8.0"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % fs2,
  "com.github.fd4s" %% "fs2-kafka" % fs2Kafka,
  "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffect % Test,
  "io.github.embeddedkafka" %% "embedded-kafka" % embeddedKafka % Test
)
