plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.freeform.unbounded"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.freeform.unbounded"
        minSdk = 31
        targetSdk = 37
        versionCode = 20260902
        versionName = "3.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Keep the local Debug/test certificate separate from the legacy certificate
    // currently used by Release. The keystore is intentionally ignored by Git.
    val localDebugKeystore = rootProject.file(".signing/mifreeform-debug.jks")
    val legacyReleaseKeystore = rootProject.file(".signing/mifreeform-release-legacy.jks")
    if (localDebugKeystore.isFile) {
        signingConfigs.create("localDebug") {
            storeFile = localDebugKeystore
            storePassword = "android"
            keyAlias = "mifreeform-debug"
            keyPassword = "android"
        }
    }
    if (legacyReleaseKeystore.isFile) {
        signingConfigs.create("legacyRelease") {
            storeFile = legacyReleaseKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (localDebugKeystore.isFile) {
                signingConfig = signingConfigs.getByName("localDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            // Preserve the existing release certificate; fall back for machines that
            // have not configured the ignored legacy keystore yet.
            signingConfig = if (legacyReleaseKeystore.isFile) {
                signingConfigs.getByName("legacyRelease")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.xposed.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.hidden.api.bypass)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
