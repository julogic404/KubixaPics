import org.gradle.api.initialization.resolve.RepositoriesMode

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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KubixaPics"

// Modulos de base partilhados
include(":core:common")
include(":core:data")
include(":core:ui")

// Modulos de funcionalidade (uma ferramenta por modulo)
include(":feature:convert")
include(":feature:resize")
include(":feature:crop")
include(":feature:compress")

// Modulo de aplicacao (shell: navegacao, marca, manifest)
include(":app")
