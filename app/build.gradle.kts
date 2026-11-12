// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds take their version from the tag (-Phospitium.versionCode=, -Phospitium.versionName=).
// The default server address is empty in this repository: an operator either builds with
// -Phospitium.serverUrl=https://... or types the address on the TV's setup screen.
val versionCodeProp = providers.gradleProperty("hospitium.versionCode").orElse("1")
val versionNameProp = providers.gradleProperty("hospitium.versionName").orElse("0.0.0-dev")
val serverUrlProp = providers.gradleProperty("hospitium.serverUrl").orElse("")

android {
    namespace = "tech.spectrumlabs.hospitium.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "tech.spectrumlabs.hospitium.launcher"
        minSdk = 28
        targetSdk = 36
        versionCode = versionCodeProp.get().toInt()
        versionName = versionNameProp.get()
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"${serverUrlProp.get().replace("\"", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned here on purpose: release signing happens outside this public repository.
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Dependency versions are Dependabot's job, not a build failure.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
