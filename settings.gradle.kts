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
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS. The Kotlin/JS and Wasm plugins add a Node
    // distribution repository from inside the plugin, and the strict mode rejects it by name before
    // anything resolves — declaring the same repository here does not satisfy it, which is worth
    // knowing because it looks like it should. Preferring settings ignores the plugin's copy and
    // uses the one below, so this list is still the whole truth about where artifacts come from.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // The Node runtime the tests run on, declared here so the version in use is visible in the
        // repository list rather than only inside the plugin. Scoped to org.nodejs:node so it is
        // never consulted for anything else.
        ivy("https://nodejs.org/dist/") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }

        // Same story for Yarn, which here does real work: it is what installs @js-joda/timezone,
        // the time-zone database JavaScript does not ship with.
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "shared-hours"
include(":hours")
include(":sample")
