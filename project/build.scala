import sbt._
import Keys._
import Def.Initialize
import Configurations.Runtime

object McpBuild extends Build {
  val mcVersion = "1.6.4"
  val wrapperVersion = "1.7"

  def reobfuscate = TaskKey[File]("reobfuscate", "Reobfuscation task")
  def ssTask: Initialize[Task[File]] =
  (baseDirectory, javaHome, streams, /*managedClasspath in Compile, */packageBin in Compile) map { (bd, jh, st, /*mc, */pb) =>
    val options = new ForkOptions(javaHome = jh, workingDirectory = some(bd))
    val runner = new ForkRun(options)
    runner.run(
      "net.md_5.specialsource.SpecialSource",
      /*mc.files :+*/ Seq(bd / "project/specialsource.jar"),
      Seq(
        "--read-inheritance", (bd / s"project/$mcVersion/nms.inheritmap").getPath,
        "--srg-in", (bd / s"project/$mcVersion/pkgmcp2numpkg.srg").getPath,
        "--in-jar", pb.getPath,
        "--out-jar", (bd / "project/output.jar").getPath,
        "--excluded-packages", "paulscode,com,isom,ibxm,de/matthiasmann/twl,org,javax/xml,javax/ws,argo"
      ),
      st.log)
    bd / "project/output.jar"
  }

  def reobfuscateTask: Initialize[Task[File]] =
  (baseDirectory, javaHome, streams, packageBin in Compile) map { (bd, jh, st, pb) =>
    val options = new ForkOptions(javaHome = jh, workingDirectory = some(bd))
    val runner = new ForkRun(options)
    runner.run(
      "immibis.bon.cui.MCPRemap",
      Seq(bd / "project/BON.jar"),
      Seq(
        "-mcp", bd.getPath,
        "-from", "MCP",
        "-to", "SRG",
        "-side", "UNIVERSAL",
        "-in", pb.getPath,
        "-out", (bd / "project/output.jar").getPath,
        "-refn", "MCP:" + (bd / "bin/minecraft").getPath
      ) ++ (bd / "run/mods" ** "*.jar").getPaths.map { f =>
        (if(f contains "-dev") "MCP:" else "OBF:") + f
      }.flatMap { f =>
        Seq("-refn", f)
      },
      st.log)
    bd / "project/output.jar"
  }

  val runJavaOptions = Seq(
    s"-Djava.library.path=../jars/versions/$mcVersion/$mcVersion-natives/",
    //"-Xdebug",
    //"-Xrunjdwp:transport=dt_socket,server=y,address=8000",
    "-Dfml.coreMods.load="
    + "rainwarrior.hooks.plugin.Plugin"
    //+ "mods.immibis.microblocks.coremod.MicroblocksCoreMod,"
    //+ "codechicken.core.launch.CodeChickenCorePlugin"
    //+ "codechicken.nei.asm.NEICorePlugin"
  )

  def buildJars: Initialize[Task[Classpath]] = Def.task {
    ((baseDirectory.value / "run/mods" ** "*.jar") +++
     runJars.value).classpath
  }

  def runJars: Initialize[Task[PathFinder]] = baseDirectory map { bd =>
    (bd / "jars/libraries" ** ("*.jar" -- "*source*")) /*+++
    ((bd / "jars/bin/") * ("*.jar" -- "minecraft.jar"))*/
  }

  def runClasspath: Initialize[Task[Classpath]] = Def.task {
      val bd = baseDirectory.value
      (Seq(
        sourceDirectory.value,
        bd / s"jars/versions/$mcVersion/$mcVersion.jar").map(Attributed.blank) ++: runJars.value.classpath
      ) ++ (
        (resourceDirectories in Compile).value :+
        (bd / s"jars/libraries/net/minecraft/launchwrapper/$wrapperVersion/launchwrapper-$wrapperVersion.jar")
      ).map(Attributed.blank)
  }

  def runOptions: Initialize[Task[ForkOptions]] = Def.task {
    ForkOptions(
      bootJars = Nil,
      javaHome = javaHome.value,
      connectInput = true,
      outputStrategy = Some(StdoutOutput),
      runJVMOptions = runJavaOptions,
      workingDirectory = Some(baseDirectory.value / "run"),
      envVars = envVars.value
    )
  }

  def runClient = TaskKey[Unit]("run-client", "Run Client Minecraft")
  def runClientTask(classpath: Initialize[Task[Classpath]]): Initialize[Task[Unit]] = Def.task {
    val runner = new ForkRun(runOptions.value)
    toError(runner.run(
      "net.minecraft.launchwrapper.Launch",
      classpath.value.map(_.data),
      Seq(
        "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker",
        "--version", "FML_DEV"
      ),
      streams.value.log
    ))
  }

  def runServer = TaskKey[Unit]("run-server", "Run Server Minecraft")
  def runServerTask(classpath: Initialize[Task[Classpath]]): Initialize[Task[Unit]] = Def.task {
    val runner = new ForkRun(runOptions.value)
    toError(runner.run(
      "cpw.mods.fml.relauncher.ServerLaunchWrapper",
      classpath.value.map(_.data),
      Seq(),
      streams.value.log
    ))
  }

  /*def runServer = TaskKey[Unit]("run-server", "Runs server")
  val serverClass = "net.minecraft.server.dedicated.DedicatedServer"
  def runServerTask = (fullClasspath in Runtime, runner in run, streams) map { (cp, rn, st) =>
    rn.run(serverClass, Attributed.data(cp), Seq(), st.log) foreach error
  }*/

  val buildSettings = Defaults.defaultSettings ++ Seq(
    name := "mcp",
    version := "1.0",
    scalaVersion := "2.10.2",
    compileOrder in Compile := CompileOrder.Mixed,
    sourceDirectory <<= baseDirectory { _ / "src/minecraft" },
    resourceDirectories in Compile <++= baseDirectory { base =>
      Seq(base / "jars/versions/1.6.2/1.6.2.jar", base / "jars") },
    classDirectory in Compile <<= baseDirectory { _ / "bin/minecraft" },
    javaSource in Compile <<= sourceDirectory,
    scalaSource in Compile <<= sourceDirectory,
    //unmanagedBase <<= baseDirectory / "jars/libraries",
    unmanagedJars in Compile <++= buildJars,
    //unmanagedClasspath in Compile <++= (resourceDirectories in Compile).toTask,
    unmanagedClasspath in Runtime <<= runClasspath,
    autoCompilerPlugins := true,
    addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.2"),
    scalacOptions ++= Seq("-P:continuations:enable", "-feature", "-deprecation", "-unchecked", "-Xlint", "-g:vars"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-g"),
    libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.2",
    libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.10.2",
    //libraryDependencies += "net.sf.jopt-simple" % "jopt-simple" % "4.4", // for SpecialSource
    //libraryDependencies += "org.ow2.asm" % "asm-debug-all" % "4.1", // for SpecialSource
    //libraryDependencies += "com.google.guava" % "guava" % "14.0-rc3", // for SpecialSource
    reobfuscate <<= reobfuscateTask,
    runClient <<= runClientTask(fullClasspath in Runtime),
    runServer <<= runServerTask(fullClasspath in Runtime)
  )

  lazy val mcp = Project(
    "mcp",
    file("."),
    settings = buildSettings
  )
  //override lazy val settings = super.settings ++ Seq(
}

