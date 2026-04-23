plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.android.library)
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xconsistent-data-class-copy-visibility",
        )
    }

    android {
        namespace = "io.gusher.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder { }
    }

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "GusherCore"
            isStatic = true
        }
    }

    compilerOptions {
        // JVM target for Android/JVM sourcesets
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.io.bytestring)
            implementation(libs.kotlinx.datetime)
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