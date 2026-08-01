#!/usr/bin/env ruby

require "fileutils"
require "open3"
require "tmpdir"

ROOT = File.expand_path("../..", __dir__)
GRADLE = ENV.fetch("GRADLE_COMMAND", File.join(ROOT, "gradlew"))
PROJECT_PROPERTIES = File.readlines(File.join(ROOT, "gradle.properties"), chomp: true).each_with_object({}) do |line, properties|
  key, value = line.split("=", 2)
  properties[key] = value if key && value
end
GROUP = PROJECT_PROPERTIES.fetch("projectGroup")
VERSION = PROJECT_PROPERTIES.fetch("baseVersion") + PROJECT_PROPERTIES.fetch("snapshotVersion", "")
ARTIFACT = "bluetape4k-graph-core"

def run!(*command, chdir:)
  stdout, stderr, status = Open3.capture3(*command, chdir: chdir)
  return stdout + stderr if status.success?

  warn(stdout)
  warn(stderr)
  abort("graph-core consumer compile smoke failed: #{command.join(" ")}")
end

run!(
  GRADLE,
  ":bluetape4k-graph-core:publishBluetapeGraphPublicationToMavenLocalRepository",
  "--no-daemon",
  "--no-configuration-cache",
  chdir: ROOT,
)

Dir.mktmpdir("graph-core-consumer") do |consumer|
  FileUtils.mkdir_p(File.join(consumer, "src/main/kotlin"))
  File.write(File.join(consumer, "settings.gradle.kts"), <<~KOTLIN)
    pluginManagement {
        repositories {
            mavenLocal()
            gradlePluginPortal()
            mavenCentral()
            maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        }
    }

    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            mavenCentral()
            maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        }
    }

    rootProject.name = "graph-core-consumer"
  KOTLIN
  File.write(File.join(consumer, "build.gradle.kts"), <<~KOTLIN)
    plugins {
        kotlin("jvm") version "2.3.21"
    }

    dependencies {
        implementation("#{GROUP}:#{ARTIFACT}:#{VERSION}")
    }

    kotlin {
        jvmToolchain(21)
    }
  KOTLIN
  File.write(File.join(consumer, "src/main/kotlin/Consumer.kt"), <<~KOTLIN)
    import io.bluetape4k.graph.model.GraphElementId
    import io.bluetape4k.graph.model.GraphVertex
    import io.bluetape4k.graph.repository.GraphSuspendTraversalRepository
    import kotlinx.coroutines.flow.Flow

    fun compileGraphSuspendFlowConsumer(repository: GraphSuspendTraversalRepository): Flow<GraphVertex> =
        repository.neighbors(GraphElementId.of("consumer-smoke"))
  KOTLIN

  output = run!(
    GRADLE,
    "-p",
    consumer,
    "compileKotlin",
    "--no-daemon",
    "--no-configuration-cache",
    chdir: ROOT,
  )
  puts "graph-core consumer compile smoke: PASS (#{GROUP}:#{ARTIFACT}:#{VERSION})"
  puts output.lines.grep(/BUILD SUCCESSFUL|compileKotlin/).last(4).join
end
