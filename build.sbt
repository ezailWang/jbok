organization in ThisBuild := "org.jbok"

name := "jbok"

description := "Just a Bunch Of Keys"

scalaVersion in ThisBuild := "2.12.4"

cancelable in Global := true

lazy val V = new {
  val circe = "0.9.1"
  val akka = "2.5.11"
  val akkaHttp = "10.1.0"
  val tsec = "0.0.1-M11"
}

lazy val fs2 = Seq(
  "co.fs2" %% "fs2-core" % "0.10.4"
)

lazy val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % V.circe)

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % V.akka,
  "com.typesafe.akka" %% "akka-stream" % V.akka,
  "com.typesafe.akka" %% "akka-slf4j" % V.akka,
  "com.typesafe.akka" %% "akka-http" % V.akkaHttp,
  "com.typesafe.akka" %% "akka-testkit" % V.akka % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % V.akka % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % V.akkaHttp % "test"
)

lazy val tests = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5",
  "org.scalacheck" %% "scalacheck" % "1.13.4"
).map(_ % "test")

lazy val logging = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
)

lazy val tsec = Seq(
  "io.github.jmcardon" %% "tsec-common" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-jca" % V.tsec,
  "io.github.jmcardon" %% "tsec-hash-bouncy" % V.tsec,
  "io.github.jmcardon" %% "tsec-signatures" % V.tsec
)

lazy val cats = Seq(
  "org.typelevel" %% "cats-core" % "1.1.0",
  "org.typelevel" %% "cats-effect" % "1.0.0-RC"
)

lazy val jbok = project
  .in(file("."))
  .aggregate(core)

lazy val common = project
  .settings(
    name := "jbok-common",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= logging ++ tests ++ cats ++ Seq(
      "org.scala-graph" %% "graph-core" % "1.12.5",
      "org.scala-graph" %% "graph-dot" % "1.12.1",
      "com.github.mpilquist" %% "simulacrum" % "0.12.0"
    )
  )

lazy val core = project
  .settings(
    name := "jbok-core"
  )
  .dependsOn(common % CompileAndTest, crypto, p2p)

lazy val crypto = project
  .settings(
    name := "jbok-crypto",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= tsec ++ circe ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
      "org.scorexfoundation" %% "scrypto" % "2.0.5"
    )
  )
  .dependsOn(common % CompileAndTest, codec)

lazy val p2p = project
  .settings(
    name := "jbok-p2p",
    libraryDependencies ++= akka ++ Seq(
      "com.lihaoyi" %% "fastparse" % "1.0.0"
    )
  )
  .dependsOn(common % CompileAndTest, crypto)

lazy val codec = project
  .settings(
    name := "jbok-codec",
    libraryDependencies ++= Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
    )
  )
  .dependsOn(common % CompileAndTest)

lazy val examples = project
  .settings(
    name := "jbok-examples"
  )
  .dependsOn(core % CompileAndTest)

lazy val app = project
  .settings(
    name := "jbok-app"
  )
  .dependsOn(core % CompileAndTest, examples)

lazy val persistent = project
  .settings(
    name := "jbok-persistent",
    libraryDependencies ++= fs2 ++ Seq(
      "org.scodec" %% "scodec-bits" % "1.1.5",
      "org.scodec" %% "scodec-core" % "1.10.3",
      "org.iq80.leveldb" % "leveldb" % "0.10",
      "io.monix" %% "monix" % "3.0.0-RC1",
      "org.scalacheck" %% "scalacheck" % "1.13.4"
    )
  )
  .dependsOn(common % CompileAndTest)

lazy val benchmark = project
  .settings(
    name := "jbok-benchmark"
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(persistent)

lazy val CompileAndTest = "compile->compile;test->test"

publishMavenStyle := true

publishArtifact in Test := false

scalacOptions in ThisBuild ++= Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8",
  "-Ypartial-unification"
)
