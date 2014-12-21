name := "mcp"

version := "1.0"

forgeVersion := "10.13.2.1236"

scalaVersion := "2.11.1"

mcVersion := "1.7.10"

coremods := Seq(
  "rainwarrior.hooks.plugin.Plugin"
  //"rainwarrior.glmod.plugin.Plugin"
  //"codechicken.core.launch.CodeChickenCorePlugin"
  //"codechicken.nei.asm.NEICorePlugin"
)

packageClasses := ((_: File) / "rainwarrior") andThen { base =>
  (base * "*.class") +++
  (base * ("trussmod" || "hooks" || "serial") ** "*.class")
}

packageResources := ((_: File) / "trussmod") andThen { base =>
  base * "models" * ("Motor.obj" || "Frame.obj") +++
  (base * "textures").***
}

buildName := "TrussMod-beta-%s-%s"

buildId := "37"

compileOrder in Compile := CompileOrder.Mixed
