val Http4sVersion = "0.20.17"
val CirceVersion = "0.11.2"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"
val ScalatagsVersion = "0.6.8"
val ScoptVersion = "4.0.0-RC2"
val ScalaVersion = "2.12.10"

val ArbeitszeitVersion = "1.0.0"

lazy val cli = (project in file("cli"))
  .settings(
    organization := "io.github.haaase",
    name := "arbeitszeit-cli",
    version := ArbeitszeitVersion,
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "org.http4s"        %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"        %% "http4s-circe"        % Http4sVersion,
      "io.circe"          %% "circe-generic"       % CirceVersion,
      "com.lihaoyi"       %% "scalatags"           % ScalatagsVersion,
      "com.github.scopt"  %% "scopt"               % ScoptVersion,
    ),
  )

lazy val web = (project in file("web")).dependsOn(cli)
  .settings(
    organization := "io.github.haaase",
    name := "arbeitszeit-web",
    version := ArbeitszeitVersion,
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "org.http4s"        %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"        %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"        %% "http4s-dsl"          % Http4sVersion,
      "ch.qos.logback"    %  "logback-classic"     % LogbackVersion
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ypartial-unification",
  "-Xfatal-warnings",
)
