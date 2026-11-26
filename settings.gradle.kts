pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted Maven Central mirror for build/test artifacts that were rate-limited
        // on shared CI addresses. Coordinates and versions remain unchanged.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "GoogleMavenCentralBuildTools"
            content {
                includeGroup("org.jetbrains.kotlin")
                includeGroup("org.ow2.asm")
                includeGroup("junit")
                includeGroup("org.hamcrest")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "HexDroidIRC"
include(":app")
