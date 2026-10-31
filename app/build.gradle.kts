import java.util.Properties
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

@CacheableTask
abstract class GenerateXposedMetadata : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templateFile: RegularFileProperty

    @get:Input abstract val moduleVersionName: Property<String>
    @get:Input abstract val moduleVersionCode: Property<Int>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDirectory.file("META-INF/xposed/module.prop").get().asFile
        target.parentFile.mkdirs()
        target.writeText(templateFile.get().asFile.readText()
            .replace("\${versionName}", moduleVersionName.get())
            .replace("\${versionCode}", moduleVersionCode.get().toString()))
    }
}

val moduleVersion = Properties().apply {
    load(providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get().reader())
}
val releaseVersionName = moduleVersion.getProperty("versionName")
val releaseVersionCode = moduleVersion.getProperty("versionCode").toInt()

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
        versionCode = releaseVersionCode
        versionName = releaseVersionName

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
        buildConfig = true
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

androidComponents {
    onVariants { variant ->
        val metadata = tasks.register<GenerateXposedMetadata>(
            "generate${variant.name.replaceFirstChar { it.uppercaseChar() }}XposedMetadata"
        ) {
            templateFile.set(layout.projectDirectory.file("src/main/xposed/module.prop"))
            moduleVersionName.set(releaseVersionName)
            moduleVersionCode.set(releaseVersionCode)
        }
        variant.sources.resources?.addGeneratedSourceDirectory(metadata, GenerateXposedMetadata::outputDirectory)
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
