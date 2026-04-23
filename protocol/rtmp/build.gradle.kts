plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.atomicfu)
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xconsistent-data-class-copy-visibility"
        )
    }

    android {
        namespace = "io.gusher.protocol.rtmp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder { }
    }

    jvm()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GusherRtmp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.io.bytestring)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
            implementation(libs.turbine)
        }
    }
}