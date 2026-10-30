plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.freeform.unbounded"
    ndkVersion = "27.1.12297006"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.freeform.unbounded"
        minSdk = 31
        targetSdk = 37
        versionCode = 20261095
        versionName = "3.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "NDK_APPLICATION_MK=${project.file("src/main/cpp/Application.mk").absolutePath}"
            }
        }

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
    // Always select the established Release identity. Gradle's signing validation
    // must fail when its keystore is missing instead of switching certificates.
    signingConfigs.create("legacyRelease") {
        storeFile = legacyReleaseKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
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
            signingConfig = signingConfigs.getByName("legacyRelease")
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
        jniLibs {
            // LSPosed injects this module into other package processes. Keep
            // the native hook libraries as real files in nativeLibraryDir so
            // the injected class loader can load them by absolute path.
            useLegacyPackaging = true
        }
        resources {
            merges += "META-INF/xposed/*"
        }
    }
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
    sourceSets.named("main") {
        // Native sources are built by Gradle. An old manually copied .so must
        // never shadow the current externalNativeBuild artifact.
        jniLibs.setSrcDirs(emptyList<String>())
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.xposed.service)
    implementation("com.bytedance:bytehook:1.1.1")
    implementation("com.bytedance.android:shadowhook:2.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.miuix.nav)
    implementation(libs.hidden.api.bypass)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
