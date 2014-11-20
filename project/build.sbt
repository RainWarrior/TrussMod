scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint")

resolvers += "gradle" at "http://repo.gradle.org/gradle/libs-releases-local"

libraryDependencies += "org.gradle" % "gradle-tooling-api" % "1.9"
