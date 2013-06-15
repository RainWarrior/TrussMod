name := "mcp"

version := "1.0"

scalaVersion := "2.10.1"

sourceDirectory <<= baseDirectory / "src/minecraft"

resourceDirectory <<= baseDirectory / "temp/bin/minecraft"

classDirectory in Compile <<= baseDirectory / "bin/minecraft"

baseDirectory in run <<= baseDirectory / "run"

javaSource in Compile <<= sourceDirectory

scalaSource in Compile <<= sourceDirectory

unmanagedJars in Compile <++= baseDirectory map { base =>
  ((base / "jars/bin/") * ("*.jar" -- "minecraft.jar")).classpath }

unmanagedClasspath in Compile <+= resourceDirectory.toTask

unmanagedClasspath in Runtime <<=
  (sourceDirectory, unmanagedClasspath in Runtime, resourceDirectory) map { (src, cp, res) =>
  (Attributed.blank(src) +: cp) :+ Attributed.blank(res) }

fork in run := true

connectInput in run := true

outputStrategy := Some(StdoutOutput)

javaOptions in run ++= Seq(
  "-Djava.library.path=../jars/bin/natives",
  "-Dfml.coreMods.load=rainwarrior.hooks.Plugin")

//mainClass in Compile := Some("Start")

autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.1")

scalacOptions ++= Seq("-P:continuations:enable", "-feature", "-deprecation")

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.1"

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.1"
