plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    android {
        namespace = "com.dancr.platform.sample"
        compileSdk = 36
        minSdk = 29
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":network-core"))
            implementation(project(":network-ktor"))
            implementation(project(":security-core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
