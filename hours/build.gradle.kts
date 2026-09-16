plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()

    android {
        namespace = "io.github.meko123456.sharedhours"
        compileSdk = 36
        // 21 rather than 26: kotlinx-datetime carries its own time-zone data on Android, so this
        // does not need the platform's java.time.
        minSdk = 21
    }

    iosArm64()
    iosSimulatorArm64()

    // A browser scheduling tool is the obvious consumer for this, so the web targets earn their
    // keep. They cost more here than they did in srs-kotlin: nothing in JavaScript ships a time-zone
    // database, so kotlinx-datetime gets one from the @js-joda/timezone npm package, and that drags
    // in the npm and Yarn toolchain.
    //
    // nodejs() only, no browser(): these declarations choose where this library's *own tests* run,
    // not where consumers can use it — the published artifacts work in a browser either way. Browser
    // test tasks add a webpack bundle and a headless Chrome to run a suite that touches no DOM.
    js(IR) {
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // The one dependency, and an unavoidable one: this is time-zone arithmetic, and that
            // needs a tz database. Everything above it here is pure integer maths.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // The tz database itself. Loading it is the part most likely to be broken on these targets,
        // which is exactly why the full suite runs on Node rather than the targets being published
        // compiled-only — every test in it asks a real zone for a real offset.
        jsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        wasmJsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.github.meko123456", "shared-hours", "0.1.0")

    pom {
        name.set("shared-hours")
        description.set(
            "When is everyone at work at the same time? Working-hours overlap across time zones " +
                "for Kotlin Multiplatform — DST-correct, weekday-aware, exhaustively tested.",
        )
        url.set("https://github.com/Meko123456/shared-hours")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("Meko123456")
                name.set("Merab Kochlamazashvili")
                url.set("https://github.com/Meko123456")
            }
        }
        scm {
            url.set("https://github.com/Meko123456/shared-hours")
            connection.set("scm:git:git://github.com/Meko123456/shared-hours.git")
            developerConnection.set("scm:git:ssh://git@github.com/Meko123456/shared-hours.git")
        }
    }
}
