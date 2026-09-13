plugins {
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// The public ABI is dumped to api/ and checked on every build, so a change to it is a reviewable
// diff rather than something a consumer discovers after a release.
apiValidation {
    ignoredProjects += "sample"

    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        // Otherwise only the JVM ABI is guarded and the native artifacts could change shape unseen.
        enabled = true
    }
}
