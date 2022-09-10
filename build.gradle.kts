// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  val kotlin = "1.7.10"
  allprojects { extra.apply { set("kotlin", kotlin) } }

  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://plugins.gradle.org/m2/") }
  }

  dependencies {
    classpath("com.android.tools.build:gradle:7.2.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
    classpath("de.mannodermaus.gradle.plugins:android-junit5:1.8.2.1")
    classpath(kotlin("serialization", version = kotlin))
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://plugins.gradle.org/m2/") }
  }
}

plugins { id("com.diffplug.spotless") version "6.9.0" }

apply(plugin = "com.diffplug.spotless")

spotless {
  kotlin {
    target("build.gradle.kts")
    ktfmt()
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
  }
}

/** Applies spotless to app projects */
subprojects {
  apply(plugin = "com.diffplug.spotless")
  spotless {
    kotlin {
      target("build.gradle.kts", "**/*.kt")
      targetExclude("$buildDir/**/*.kt")
      ktfmt()
      lineEndings = com.diffplug.spotless.LineEnding.UNIX
    }
  }
}
