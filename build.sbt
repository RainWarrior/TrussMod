name := "mcp"

version := "1.0"

scalaVersion := "2.10.2"

mcVersion := "1.7.2"

wrapperVersion := "1.9"

packageRegex := """(assets/trussmod/(models/(Motor|Frame)\.obj|textures/.*)|rainwarrior/(trussmod/.*|hooks/.*|serial/.*|[^/]*)|gnu/.*)"""

buildName := "TrussMod-beta-%s-%s"

buildId := "34"

compileOrder in Compile := CompileOrder.Mixed
