name := "poloniex"

//common settings for the project and subprojects
lazy val commonSettings = Seq(
	organization := "com.flowmaster",
	version := "0.1.2",
	scalaVersion := "2.11.8",
	scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-target:jvm-1.8")
)

lazy val root = (project in file("."))
	.settings(commonSettings: _*)
	.settings(routesGenerator := InjectedRoutesGenerator)
	.settings(
		libraryDependencies += ws,
		libraryDependencies += "com.github.angiolep" % "akka-wamp_2.11" % "0.6.1-SNAPSHOT",
		libraryDependencies += "com.typesafe.slick" %% "slick" % "3.1.1",
		libraryDependencies += "com.typesafe.slick" %% "slick-codegen" % "3.1.1",
		libraryDependencies += "com.github.tminglei" %% "slick-pg" % "0.14.3",
		libraryDependencies += "com.github.tminglei" %% "slick-pg_date2" % "0.14.3",
		libraryDependencies += "com.typesafe.play" %% "play-slick" % "2.0.2",
		libraryDependencies += "com.typesafe.play" %% "play-slick-evolutions" % "2.0.2",
		libraryDependencies += "jp.t2v" %% "play2-auth" % "0.14.2",
		libraryDependencies += play.sbt.Play.autoImport.cache,
		libraryDependencies += "com.github.t3hnar" %% "scala-bcrypt" % "2.6",
		libraryDependencies += "org.webjars" %% "webjars-play" % "2.5.0",
		libraryDependencies += "org.webjars" % "foundation" % "6.2.3",
		libraryDependencies += "com.typesafe.play" %% "play-mailer" % "5.0.0"
	)
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)

//to generate models/db/Tables.scala
addCommandAlias("tables", "run-main utils.db.SourceCodeGenerator")
