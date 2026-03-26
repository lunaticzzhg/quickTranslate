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
        buildConfigField(
            "int",
            "WHISPER_THREADS",
            providers.gradleProperty("quicktranslate.whisper.threads")
                .orElse("0")
                .get()
        )
        buildConfigField(
            "String",
            "YTDLP_PATH",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.ytdlp.path")
                    .orElse("")
                    .get()
            )
        )
        buildConfigField(
            "String",
            "YTDLP_COOKIES_PATH",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.ytdlp.cookies.path")
                    .orElse("")
                    .get()
            )
        )
        buildConfigField(
            "String",
            "YTDLP_EXTRACTOR_ARGS",
            quoteForBuildConfig(
                providers.gradleProperty("quicktranslate.ytdlp.extractor.args")
                    .orElse("youtube:player_client=tv,web_safari")
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
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.androidx.compose)
}
