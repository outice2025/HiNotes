import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so the separate
    // `org.jetbrains.kotlin.android` plugin is neither needed nor allowed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing material lives in keystore.properties (git-ignored).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

val appVersionName = "1.0.5"
val appVersionCode = 15

android {
    namespace = "com.hiapps.hinotes"
    // Compose 1.12 / AndroidX require compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hiapps.hinotes"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            // Paths in keystore.properties are relative to the project root.
            val storePath = keystoreProps.getProperty("storeFile")
            val store = storePath?.let { rootProject.file(it) }
            if (store != null && store.exists()) {
                storeFile = store
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Name the deliverable after the app and version rather than the Gradle default.
    // Uses the variant API (the deprecated `applicationVariants` one is gone in AGP 9).
    androidComponents.onVariants { variant ->
        val signed = variant.buildType == "release" &&
            keystoreProps.getProperty("storeFile")
                ?.let { rootProject.file(it).exists() } == true
        val suffix = if (signed) "release" else "unsigned"
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("HiNotes-$appVersionName-$suffix.apk")
        }
        // Keep a copy of every release outside `build/`, so a later build (or a `clean`)
        // cannot destroy the previous deliverable. The lookup is deferred because AGP
        // registers the variant's `assemble*` task after this callback runs.
        if (variant.buildType == "release") {
            val taskName = "assemble${variant.name.replaceFirstChar(Char::uppercase)}"
            afterEvaluate {
                tasks.named(taskName) { finalizedBy(archiveReleaseArtifacts) }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

kotlin {
    compilerOptions {
        // jvmTarget defaults to android.compileOptions.targetCompatibility under built-in Kotlin.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * Keeps a copy of each release APK under `<project>/releases/v<version>/`.
 *
 * This runs automatically after `assembleRelease`. The archive lives outside `build/` on
 * purpose: Gradle wipes `build/` on `clean`, which would otherwise destroy the previous
 * deliverable, and rebuilding the same version overwrites that version's folder rather than
 * piling up duplicates.
 */
val archiveReleaseArtifacts = tasks.register<Copy>("archiveReleaseArtifacts") {
    group = "build"
    description = "Copies the release APK into releases/v<version>/ so earlier builds survive."

    dependsOn("packageRelease")

    // The output file name is fixed by the variant configuration above, so it can be named
    // directly rather than discovered by listing the output directory.
    val apkName = "HiNotes-$appVersionName-release.apk"
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")

    from(apkDir.map { it.file(apkName) })
    into(rootProject.layout.projectDirectory.dir("releases/v$appVersionName"))

    doLast {
        val target = rootProject.file("releases/v$appVersionName/$apkName")
        require(target.isFile) { "Release APK was not found at $target" }
        logger.lifecycle("Archived release build: releases/v$appVersionName/$apkName")
    }
}
