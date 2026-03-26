fun quoteForBuildConfig(raw: String): String {
    val escaped = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lunatic.quicktranslate.feature.session"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        buildConfigField(
            "String",
            "TRANSCRIPTION_MODE",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.transcription.mode")
                    .orElse("mock")
                    .get()
            )
        )
        buildConfigField(
            "String",
            "WHISPER_CLI_PATH",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.whisper.cli.path")
                    .orElse("")
                    .get()
            )
        )
        buildConfigField(
            "String",
            "WHISPER_MODEL_PATH",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.whisper.model.path")
                    .orElse("")
                    .get()
            )
        )
        buildConfigField(
            "String",
            "WHISPER_LANGUAGE",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.whisper.language")
                    .orElse("en")
                    .get()
            )
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":player:core"))
    implementation(project(":feature:transcription"))
    implementation(project(":domain:project"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.androidx.compose)
}
