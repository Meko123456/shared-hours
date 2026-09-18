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
        // Deliberately 36 while every app in this fleet is on 37, and not an oversight.
        //
        // AGP writes a library's compileSdk straight into the published AAR as minCompileSdk, so
        // this value is a requirement placed on everyone who depends on the library rather than a
        // private build detail. Verified on heatmap-compose rather than assumed: building that
        // module on 37 produced minCompileSdk=37 in its aar-metadata.properties. It is the exact
        // mechanism by which Compose BOM 2026.09.00 and okhttp 5.5.0 broke projects across this
        // fleet all week.
        //
        // Its one dependency is kotlinx-datetime, which asks for nothing newer, and an adopter should not
        // have to move compile target to get working-hours arithmetic.
        // It moves when something here actually needs an API newer than 36, and not before.
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
                "for Kotlin Multiplatform — DST-correct, weekday-aware, split shifts, " +
                "exhaustively tested on JVM, Android, iOS, JS and Wasm.",
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
