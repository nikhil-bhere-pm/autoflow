plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.autoflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autoflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0-test1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val ksPath = System.getenv("AUTOFLOW_KS_PATH")
    val ksPass = System.getenv("AUTOFLOW_KS_PASS")
    val keyAlias = System.getenv("AUTOFLOW_KEY_ALIAS")
    val keyPass = System.getenv("AUTOFLOW_KEY_PASS")
    val hasReleaseSigning = !ksPath.isNullOrBlank() && File(ksPath).exists()

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = File(ksPath)
                storePassword = ksPass
                this.keyAlias = keyAlias
                this.keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // Disabled for first tester build: lower crash risk with Room and WorkManager
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Parser Core (AGPL-3.0)
    implementation(project(":parser-core"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // WorkManager (Apache 2.0)
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
