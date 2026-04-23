plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "io.gusher"
    version = "0.1.0-SNAPSHOT"
}