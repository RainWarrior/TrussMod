import sbt._
import Keys._
import Def.Initialize
import Configurations.Runtime

import java.util.Properties

object McpBuild extends Build {

  val mcVersion = settingKey[String]("minecraft version")
  val wrapperVersion = settingKey[String]("launchwrapper version")
  val packageRegex = settingKey[String]("package selection regex")
  val buildName = settingKey[String]("build name")
  val buildId = settingKey[String]("build id")

  val reobfuscate = taskKey[File]("Reobfuscation task")
  def ssTask: Initialize[Task[File]] = Def.task {
    val bd = baseDirectory.value
    val options = new ForkOptions(javaHome = Keys.javaHome.value, workingDirectory = some(bd))
    val runner = new ForkRun(options)
    //val mcVersion = pr.getProperty("mcVersion")
    runner.run(
      "net.md_5.specialsource.SpecialSource",
      /*mc.files :+*/ Seq(bd / "project/specialsource.jar"),
      Seq(
        "--read-inheritance", (bd / s"project/${mcVersion.value}/nms.inheritmap").getPath,
        "--srg-in", (bd / s"project/${mcVersion.value}/pkgmcp2numpkg.srg").getPath,
        "--in-jar", (packageBin in Compile).value.getPath,
        "--out-jar", (bd / "project/output.jar").getPath,
        "--excluded-packages", "paulscode,com,isom,ibxm,de/matthiasmann/twl,org,javax/xml,javax/ws,argo"
      ),
      streams.value.log)
    bd / "project/output.jar"
  }

  def bonTask = Def.task {
    val bd = baseDirectory.value
    val options = new ForkOptions(javaHome = Keys.javaHome.value, workingDirectory = some(bd))
    val runner = new ForkRun(options)
    runner.run(
      "immibis.bon.cui.MCPRemap",
      Seq(bd / "project/BON.jar"),
      Seq(
        "-mcp", bd.getPath,
        "-from", "MCP",
        "-to", "SRG",
        "-side", "UNIVERSAL",
        "-in", (packageBin in Compile).value.getPath,
        "-out", (bd / "project/output.jar").getPath,
        "-refn", "MCP:" + (bd / "bin/minecraft").getPath
      ) ++ (bd / "run/mods" ** "*.jar").getPaths.map { f =>
        (if(f contains "-dev") "MCP:" else "OBF:") + f
      }.flatMap { f =>
        Seq("-refn", f)
      },
      streams.value.log)
    bd / "project/output.jar"
  }

  def treeTask = Def.task {
    val bd = baseDirectory.value
    val options = new ForkOptions(javaHome = Keys.javaHome.value, workingDirectory = some(bd))
    val runner = new ForkRun(options)
    val inJar = (packageBin in Compile).value.getPath
    runner.run(
      "com.simontuffs.onejar.Boot",
      Seq(bd / "project/tree_obfuscator.jar"),
      Seq(
        "-cf", inJar,
        "-s", (bd / "project/mcp2srg.srg").getPath,
        s"-c:$inJar=" + (bd / "project/output.jar").getPath
        //(bd / "bin/minecraft").getPath
      ) ++ ((bd / "run/mods" ** "*.jar").getPaths ++ (bd / "lib/mods" ** "*.jar").getPaths).flatMap { f =>
        Seq(if((f contains "-dev") || (f contains "-deo")) "-cf" else "-ct", f)
      },
      streams.value.log)
    bd / "project/output.jar"
  }

  val `package` = taskKey[File]("Mod package task")
  def packageTask: Initialize[Task[File]] = Def.task {
    import sbt.IO._

    val pd = baseDirectory.value / "project"
    val manifest = new java.util.jar.Manifest(new java.io.FileInputStream(pd / "MANIFEST.MF"))
    val bn = buildName.value.format(mcVersion.value, buildId.value)
    val filter = packageRegex.value.r.pattern
    val nameFilter = new NameFilter { def accept(f: String): Boolean = filter.matcher(f).matches }
    val outFile = pd / s"$bn.jar"
    withTemporaryDirectory { temp =>
      unzip((reobfuscate in Compile).value, temp, nameFilter)
      for(f <- Seq("mcmod.info", "LICENSE", "COPYING", "README"))
        copyFile(pd / f, temp / f)
      jar((temp ** "*").get.map { f =>
          (f, temp.toPath.relativize(f.toPath).toString)
        }, outFile, manifest)
    }
    //delete(pd / "reobf")
    //createDirectory(pd / "reobf")
    outFile
  }

  def runJavaOptions(mcVersion: String) = Seq(
    s"-Djava.library.path=../build/natives/",
    "-Dfml.debugAPITransformer=true",
    "-Xdebug",
    "-Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n",
    "-Dfml.coreMods.load="
    + "rainwarrior.hooks.plugin.Plugin"
    //+ "mods.immibis.microblocks.coremod.MicroblocksCoreMod,"
    //+ "codechicken.core.launch.CodeChickenCorePlugin"
    //+ "codechicken.nei.asm.NEICorePlugin"
  )

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
      val wv = wrapperVersion.value
      (Seq(
        sourceDirectory.value,
        bd / "run",
        bd / s"jars/minecraft-$v.jar"
      ).map(Attributed.blank) ++: runJars.value.classpath) ++ (
        (resourceDirectories in Compile).value :+
        (bd / s"jars/libraries/launchwrapper-$wv.jar")
      ).map(Attributed.blank)
  }

  def runOptions: Initialize[Task[ForkOptions]] = Def.task {
    ForkOptions(
      bootJars = Nil,
      javaHome = javaHome.value,
      connectInput = true,
      outputStrategy = Some(StdoutOutput),
      runJVMOptions = runJavaOptions(mcVersion.value),
      workingDirectory = Some(baseDirectory.value / "run"),
      envVars = envVars.value
    )
  }

  val runClient = taskKey[Unit]("Run Client Minecraft")
  def runClientTask: Initialize[Task[Unit]] = Def.task {
    val runner = new ForkRun(runOptions.value)
    toError(runner.run(
      "net.minecraft.launchwrapper.Launch",
      (fullClasspath in Runtime).value.map(_.data),
      Seq(
        "--version", "1.7",
        "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker",
        "--username", "ForgeDevName",
        "--accessToken", "FML"
      ),
      streams.value.log
    ))
  }

  val runServer = taskKey[Unit]("Run Server Minecraft")
  def runServerTask: Initialize[Task[Unit]] = Def.task {
    val runner = new ForkRun(runOptions.value)
    toError(runner.run(
      "cpw.mods.fml.relauncher.ServerLaunchWrapper",
      (fullClasspath in Runtime).value.map(_.data),
      Seq(),
      streams.value.log
    ))
  }

  val runStart = taskKey[Unit]("Run MCP Start class")
  def runStartTask: Initialize[Task[Unit]] = Def.task {
    val runner = new ForkRun(runOptions.value)
    toError(runner.run(
      "Start",
      (fullClasspath in Runtime).value.map(_.data),
      Seq(),
      streams.value.log
    ))
  }

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
    addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.2"),
    scalacOptions ++= Seq("-P:continuations:enable", "-feature", "-deprecation", "-unchecked", /*"-optimise", */"-Xlint", "-g:vars", "-Yinline-warnings"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-g"),
    resolvers += "forge" at "http://files.minecraftforge.net/maven",
    //libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies += "org.ow2.asm" % "asm-debug-all" % "4.1",
    libraryDependencies += "net.sf.jopt-simple" % "jopt-simple" % "4.5",
    libraryDependencies += "java3d" % "vecmath" % "1.3.1",
    libraryDependencies += "net.sf.trove4j" % "trove4j" % "3.0.3",
    //libraryDependencies += "com.ibm.icu" % "icu4j-core-mojang" % "51.2",
    //libraryDependencies += "lzma" % "lzma" % "0.0.1",
    //libraryDependencies += "com.paulscode" % "codecjorbis" % "20101023",
    //libraryDependencies += "com.paulscode" % "codecwav" % "20101023",
    //libraryDependencies += "com.paulscode" % "libraryjavasound" % "20101123",
    //libraryDependencies += "com.paulscode" % "librarylwjglopenal" % "20100824",
    //libraryDependencies += "com.paulscode" % "soundsystem" % "20120107",
    libraryDependencies += "io.netty" % "netty-all" % "4.0.10.Final",
    libraryDependencies += "com.google.guava" % "guava" % "15.0",
    libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    libraryDependencies += "net.java.jinput" % "jinput" % "2.0.5",
    libraryDependencies += "net.java.jutils" % "jutils" % "1.0.0",
    libraryDependencies += "com.google.code.gson" % "gson" % "2.2.4",
    //libraryDependencies += "com.mojang" % "authlib" % "1.3",
    libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.0-beta9",
    libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.0-beta9",
    //libraryDependencies += "" % "" % "",
    libraryDependencies += "org.lwjgl.lwjgl" % "lwjgl" % "2.9.0",
    libraryDependencies += "org.lwjgl.lwjgl" % "lwjgl_util" % "2.9.0",
    libraryDependencies += "org.lwjgl.lwjgl" % "lwjgl-platform" % "2.9.0",
    //libraryDependencies += "net.sf.jopt-simple" % "jopt-simple" % "4.4", // for SpecialSource
    //libraryDependencies += "org.ow2.asm" % "asm-debug-all" % "4.1", // for SpecialSource
    //libraryDependencies += "com.google.guava" % "guava" % "14.0-rc3", // for SpecialSource
    reobfuscate in Compile := treeTask.value,
    `package` in Compile := packageTask.value,
    runClient in Runtime := runClientTask.value,
    runServer in Runtime := runServerTask.value,
    runStart in Runtime := runStartTask.value
  )

  lazy val mcp = Project(
    "mcp",
    file("."),
    settings = buildSettings
  )
}

