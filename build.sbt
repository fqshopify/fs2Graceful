ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "fs2Graceful"
  )

val fs2 = "2.5.10"
val fs2Kafka = "1.10.0"
val munitCatsEffect = "1.0.0"
val embeddedKafka = "2.8.0"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % fs2,
  "com.github.fd4s" %% "fs2-kafka" % fs2Kafka,
  "org.typelevel" %% "munit-cats-effect-2" % munitCatsEffect % Test,
  "io.github.embeddedkafka" %% "embedded-kafka" % embeddedKafka % Test
)
