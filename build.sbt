name := "mcp"

version := "1.0"

scalaVersion := "2.10.2"

mcVersion := "1.6.4"

wrapperVersion := "1.7"

packageRegex := """(assets/trussmod/(models/(Motor|Frame)\.obj|textures/.*)|rainwarrior/(trussmod/.*|hooks/.*|[^/]*)|gnu/.*)"""

buildName := "TrussMod-beta-%s-%s"

buildId := "32"

compileOrder in Compile := CompileOrder.Mixed
