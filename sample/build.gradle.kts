plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":hours"))
}

application {
    mainClass.set("io.github.meko123456.sharedhours.sample.MainKt")
}
