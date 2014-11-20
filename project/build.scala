import sbt._
import Keys._
import Def.Initialize
import Configurations.Runtime

import java.util.Properties

object McpBuild extends Build {

  val mcVersion = settingKey[String]("minecraft version")
  val forgeVersion = settingKey[String]("forge version")
  val coremods = settingKey[Seq[String]]("fml coremods to load")
  val packageClasses = settingKey[File => PathFinder]("classes to package")
  val packageResources = settingKey[File => PathFinder]("resources to package")
  val buildName = settingKey[String]("build name")
  val buildId = settingKey[String]("build id")
  val gradleDecompWorkspace = taskKey[Unit]("call gradle setupDecompWorkspace task")
  val mcSource = settingKey[File]("minecraft source artifact")
  val extractMcSource = taskKey[Unit]("extract minecraft source")
  val reobfuscate = taskKey[File]("Reobfuscation task")
  val `package` = taskKey[File]("Mod package task")
  val cleanForge = taskKey[Unit]("clean forge dir")

  def forgeFull = Def.setting(s"${mcVersion.value}-${forgeVersion.value}")
  def assetsDir = Def.setting(baseDirectory.value / "bin/assets")

  /*def runFF(ff: File, in: File, out: File): Unit = {
    val options = ForkOptions()
    val ret: Int = Fork.java(options, Seq(
      "-jar", ff.getPath,
      "-din=1", "-rbr=0", "-dgs=1", "asc=1", "-log=ERROR",
      in.getPath, out.getPath
    ))
  }*/

  def callGradleTask(baseDir: File, task: String): Unit = {
    import org.gradle.tooling.GradleConnector
    val connector = GradleConnector.newConnector forProjectDirectory baseDir connect()
    try {
      connector.newBuild().forTasks(task).run()
    } finally {
      connector.close()
    }
  }

  def setupAndCallDecomp(baseDir: File, version: String): Unit = {
    val bg = IO read (baseDir / "build.gradle.template")
    IO write (baseDir / "forge/build.gradle", bg format version)
    callGradleTask(baseDir / "forge", "setupDecompWorkspace")
  }

  def runJavaOptions = Def.setting(Seq(
    s"-Djava.library.path=../forge/build/natives/",
    "-Dfml.debugAPITransformer=true",
    //"-Xdebug",
    //"-Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n",
    "-Dfml.coreMods.load="+ coremods.value.mkString(",")
  ))

  def buildJars: Initialize[Task[Classpath]] = Def.task {
    ((baseDirectory.value / "run/mods" ** "*.jar") +++
     (baseDirectory.value / "lib" ** "*.jar") +++
     runJars.value).classpath
  }

  def runJars: Initialize[Task[PathFinder]] = Def.task {
    (baseDirectory.value / "jars/libraries" ** ("*.jar" -- "*source*")) /*+++
    ((baseDirectory.value / "jars/bin/") * ("*.jar" -- "minecraft.jar"))*/
  }

  def runClasspath: Initialize[Task[Classpath]] = Def.task {
      val bd = baseDirectory.value
      val v = mcVersion.value
      (Seq(
        sourceDirectory.value,
        bd / "run",
        bd / s"jars/minecraft-$v.jar"
      ).map(Attributed.blank) ++: runJars.value.classpath) ++ (
        (resourceDirectories in Compile).value
      ).map(Attributed.blank)
  }

  def runOptions: Initialize[Task[ForkOptions]] = Def.task {
    ForkOptions(
      //bootJars = Nil,
      //javaHome = javaHome.value,
      connectInput = true,
      outputStrategy = Some(StdoutOutput),
      runJVMOptions = runJavaOptions.value,
      workingDirectory = Some(baseDirectory.value / "run")
      //envVars = envVars.value
    )
  }

  val runClient = taskKey[Unit]("Run Client Minecraft")
  val runServer = taskKey[Unit]("Run Server Minecraft")
  val runStart = taskKey[Unit]("Run MCP Start class")

  val buildSettings = Defaults.defaultSettings ++ Seq(
    sourceDirectory := baseDirectory.value / "src",
    classDirectory in Compile := baseDirectory.value / "bin",
    javaSource in Compile := sourceDirectory.value,
    scalaSource in Compile := sourceDirectory.value,
    unmanagedJars in Compile ++= buildJars.value,
    unmanagedClasspath in Compile ++= Seq(
      baseDirectory.value / s"jars/versions/${mcVersion.value}/${mcVersion.value}.jar",
      baseDirectory.value / "jars"
    ),
    unmanagedClasspath in Runtime := runClasspath.value,
    autoCompilerPlugins := true,
    scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-optimise", "-Xlint", "-g:vars", "-Yinline-warnings"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-g"),
    resolvers += "forge" at "http://files.minecraftforge.net/maven",
    resolvers += "minecraft" at "https://libraries.minecraft.net/",
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    crossScalaVersions := Seq(scalaVersion.value),
    // TODO pull from json
    libraryDependencies ++= Seq(
      "com.nativelibs4java" %% "scalaxy-streams" % "0.3.2" % "provided",
      //"org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
      "net.minecraft" % "launchwrapper" % "1.11",
      "com.google.code.findbugs" % "jsr305" % "1.3.9",
      "org.ow2.asm" % "asm-debug-all" % "5.0.3",
      "com.typesafe.akka" %% "akka-actor" % "2.3.3",
      "com.typesafe" % "config" % "1.2.1",
      "org.scala-lang" %% "scala-actors-migration" % "1.1.0",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scala-lang.plugins" %% "scala-continuations-library" % "1.0.2",
      "org.scala-lang.plugins" % "scala-continuations-plugin_2.11.1" % "1.0.2",
      //"org.scala-lang" %% "scala-parser-combinators" % "1.0.1",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      //"org.scala-lang" %% "scala-swing" % "1.0.1",
      //"org.scala-lang" %% "scala-xml" % "1.0.2",
      "net.sf.jopt-simple" % "jopt-simple" % "4.5",
      "lzma" % "lzma" % "0.0.1",
      "com.mojang" % "realms" % "1.3.5",
      "org.apache.commons" % "commons-compress" % "1.8.1",
      "org.apache.httpcomponents" % "httpclient" % "4.3.3",
      "commons-logging" % "commons-logging" % "1.1.3",
      "org.apache.httpcomponents" % "httpcore" % "4.3.2",
      "java3d" % "vecmath" % "1.3.1",
      "net.sf.trove4j" % "trove4j" % "3.0.3",
      "com.ibm.icu" % "icu4j-core-mojang" % "51.2",
      "com.paulscode" % "codecjorbis" % "20101023",
      "com.paulscode" % "codecwav" % "20101023",
      "com.paulscode" % "libraryjavasound" % "20101123",
      "com.paulscode" % "librarylwjglopenal" % "20100824",
      "com.paulscode" % "soundsystem" % "20120107",
      "io.netty" % "netty-all" % "4.0.10.Final",
      "com.google.guava" % "guava" % "16.0",
      "org.apache.commons" % "commons-lang3" % "3.2.1",
      "commons-io" % "commons-io" % "2.4",
      "commons-codec" % "commons-codec" % "1.9",
      "net.java.jinput" % "jinput" % "2.0.5",
      "net.java.jutils" % "jutils" % "1.0.0",
      "com.google.code.gson" % "gson" % "2.2.4",
      "com.mojang" % "authlib" % "1.5.16",
      "org.apache.logging.log4j" % "log4j-api" % "2.0-beta9",
      "org.apache.logging.log4j" % "log4j-core" % "2.0-beta9",
      "org.lwjgl.lwjgl" % "lwjgl" % "2.9.1",
      "org.lwjgl.lwjgl" % "lwjgl_util" % "2.9.1",
      "org.lwjgl.lwjgl" % "lwjgl-platform" % "2.9.1",
      "tv.twitch" % "twitch" % "5.16",
      "tv.twitch" % "twitch-platform" % "5.16"
    ),
    reobfuscate in Compile := {
      val bd = baseDirectory.value
      val options = new ForkOptions(javaHome = Keys.javaHome.value, workingDirectory = some(bd))
      val runner = new ForkRun(options)
      val inJar = (packageBin in Compile).value.getPath
      runner.run(
        "com.simontuffs.onejar.Boot",
        Seq(bd / "project/tree_obfuscator.jar"),
        Seq(
          "-cf", inJar,
          "-s", (Path.userHome / s".gradle/caches/minecraft/net/minecraftforge/forge/${forgeFull.value}/srgs/mcp-srg.srg").getPath,
          s"-c:$inJar=" + (bd / "project/output.jar").getPath
          //(bd / "bin/minecraft").getPath
        ) ++ ((bd / "run/mods" ** "*.jar").getPaths ++ (bd / "lib/mods" ** "*.jar").getPaths).flatMap { f =>
          Seq(if((f contains "-dev") || (f contains "-deo")) "-cf" else "-ct", f)
        },
        streams.value.log)
      bd / "project/output.jar"
    },
    `package` in Compile := {
      import sbt.IO._

      val pd = baseDirectory.value / "project"
      val manifest = new java.util.jar.Manifest(new java.io.FileInputStream(pd / "MANIFEST.MF"))
      val bn = buildName.value.format(mcVersion.value, buildId.value)
      val outFile = pd / s"$bn.jar"
      val static = Seq("mcmod.info", "LICENSE", "COPYING", "README")
      withTemporaryDirectory { temp =>
        unzip((reobfuscate in Compile).value, temp)
        jar(
          (for(f <- static) yield (pd / f, f)) ++
            ((packageClasses.value(temp) +++ packageResources.value(temp / "assets")) pair Path.relativeTo(temp)),
          outFile, manifest
        )
      }
      outFile
    },
    runClient in Runtime := {
      (baseDirectory.value / "run").mkdir()
      val runner = new ForkRun(runOptions.value)
      toError(runner.run(
        "net.minecraft.launchwrapper.Launch",
        (fullClasspath in Runtime).value.map(_.data),
        Seq(
          "--version", "1.7",
          "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker",
          "--username", "ForgeDevName",
          "--accessToken", "FML",
          "--assetsDir", (Path.userHome / ".gradle/caches/minecraft/assets").getPath,
          "--assetIndex", mcVersion.value,
          "--userProperties", "{}"
        ),
        streams.value.log
      ))
    },
    runServer in Runtime := {
      (baseDirectory.value / "run").mkdir()
      val runner = new ForkRun(runOptions.value)
      toError(runner.run(
        "cpw.mods.fml.relauncher.ServerLaunchWrapper",
        (fullClasspath in Runtime).value.map(_.data),
        Seq(),
        streams.value.log
      ))
    },
    runStart in Runtime := {
      (baseDirectory.value / "run").mkdir()
      val runner = new ForkRun(runOptions.value)
      toError(runner.run(
        "Start",
        (fullClasspath in Runtime).value.map(_.data),
        Seq(),
        streams.value.log
      ))
    },
    gradleDecompWorkspace := setupAndCallDecomp(baseDirectory.value, forgeFull.value),
    mcSource := {
      val artifact: File = baseDirectory.value / s"forge/build/dirtyArtifacts/forgeSrc-${forgeFull.value}-sources.jar"
      if(!artifact.exists) {
        setupAndCallDecomp(baseDirectory.value, forgeFull.value)
        if(!artifact.exists) {
          IO.copyFile(Path.userHome / s".gradle/caches/minecraft/net/minecraftforge/forge/${forgeFull.value}/forgeSrc-${forgeFull.value}-sources.jar", artifact)
        }
      }
      artifact
    },
    extractMcSource := {
      sourceDirectory.value.mkdir()
      IO.unzip(mcSource.value, sourceDirectory.value)
      val a2 = sourceDirectory.value / "assets"
      IO.move((a2 * "*") pair Path.rebase(a2, assetsDir.value))
      IO.delete(a2)
    },
    cleanForge := IO.delete(baseDirectory.value / "forge")
  )

  lazy val root = Project(
    "root",
    file("."),
    settings = buildSettings
  )
}

