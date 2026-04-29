// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

// Expose group/version on every subproject so Gradle composite-build substitution
// (consumers using includeBuild(...)) resolves "io.github.dancrrdz93:network-core",
// "io.github.dancrrdz93:network-ktor", etc. automatically. The vanniktech plugin
// already reads GROUP/VERSION_NAME from gradle.properties for POM coordinates;
// here we additionally apply them to the runtime Project so the dependency
// substitution layer can match them.
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}