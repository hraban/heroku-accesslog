import NativePackagerKeys._

packageArchetype.java_application

name := """accesslog"""

version := "0.1"

scalaVersion := "2.10.4"
  
libraryDependencies ++= Seq(
  "com.twitter" % "finagle-httpx_2.11" % "6.24.0",
  "com.twitter" % "finagle-http_2.11" % "6.24.0",
  "com.twitter" % "util-logging_2.11" % "6.23.0",
  "postgresql" % "postgresql" % "9.0-801.jdbc4"
)
