// Based on sbt-protobuf's Protobuf Plugin
// https://github.com/sbt/sbt-protobuf

package com.trueaccord.scalapb

import java.io.File

import com.trueaccord.scalapb.compiler.{PosixProtocDriver, WindowsProtocDriver, ProtocDriver}
import sbt.Keys._
import sbt._
import sbtprotobuf.{ProtobufPlugin => PB}

object ScalaPbPlugin extends Plugin {
  // Set up aliases to SbtProtobuf tasks
  val includePaths = PB.includePaths
  val protoc = PB.protoc
  val runProtoc = TaskKey[Seq[String] => Int]("scalapb-run-protoc", "A function that executes the protobuf compiler with the given arguments, returning the exit code of the compilation run.")
  val externalIncludePath = PB.externalIncludePath
  val generatedTargets = PB.generatedTargets
  val generate = PB.generate
  val unpackDependencies = PB.unpackDependencies
  val protocOptions = PB.protocOptions
  val javaConversions = SettingKey[Boolean]("scalapb-java-conversions", "Generate Scala-Java protocol buffer conversions")
  val flatPackage = SettingKey[Boolean]("scalapb-flat-package", "Do not generate a package for each file")
  val grpc = SettingKey[Boolean]("scalapb-grpc", "Generate Grpc stubs for services")
  val scalapbVersion =  SettingKey[String]("scalapb-version", "ScalaPB version.")
  val pythonExecutable =  SettingKey[String]("python-executable", "Full path for a Python.exe (needed only on Windows)")

  val protobufConfig = PB.protobufConfig

  val protocDriver = TaskKey[ProtocDriver]("scalapb-protoc-driver", "Protoc driver")

  val protobufSettings = PB.protobufSettings ++ inConfig(protobufConfig)(Seq[Setting[_]](
    scalaSource <<= (sourceManaged in Compile) { _ / "compiled_protobuf" },

    javaConversions := false,
    grpc := true,
    flatPackage := false,
    scalapbVersion := com.trueaccord.scalapb.plugin.Version.scalaPbVersion,
    pythonExecutable := "python",
    protocDriver <<= protocDriverTask,
    generatedTargets <<= (javaConversions in PB.protobufConfig,
      javaSource in PB.protobufConfig, scalaSource in PB.protobufConfig) {
      (javaConversions, javaSource, scalaSource) =>
        (scalaSource, "*.scala") +:
          (if (javaConversions)
            Seq((javaSource, "*.java"))
          else
            Nil)
    },
    version := "2.6.1",
    // Set protobuf's runProtoc runner with our runner..
    runProtoc <<= (protoc, streams) map ((cmd, s) => args => Process(cmd, args) ! s.log),
    PB.runProtoc := protocDriver.value.buildRunner((runProtoc in PB.protobufConfig).value),
    protocOptions <++= (generatedTargets in protobufConfig,
                        javaConversions in protobufConfig,
                        flatPackage in protobufConfig,
                        grpc in protobufConfig) {
      (generatedTargets, javaConversions, flatPackage, grpc) =>
      def makeParams(params: (Boolean, String)*) = params
        .collect {
          case (true, paramName) => paramName
        }.mkString(",")
      generatedTargets.find(_._2.endsWith(".scala")) match {
        case Some(targetForScala) =>
          val params = makeParams(
            javaConversions -> "java_conversions",
            flatPackage -> "flat_package",
            grpc -> "grpc")
          Seq(s"--scala_out=$params:${targetForScala._1.absolutePath}")
        case None => Nil
      }
    })) ++ Seq[Setting[_]](
    libraryDependencies <++= (scalapbVersion in protobufConfig) {
      runtimeVersion =>
        Seq(
          "com.trueaccord.scalapb" %% "scalapb-runtime" % runtimeVersion
        )
    })

  private def isWindows: Boolean = sys.props("os.name").startsWith("Windows")

  private def protocDriverTask = (pythonExecutable in protobufConfig) map {
    pythonExecutable =>
      if (isWindows) new WindowsProtocDriver(pythonExecutable)
      else new PosixProtocDriver
  }
}
