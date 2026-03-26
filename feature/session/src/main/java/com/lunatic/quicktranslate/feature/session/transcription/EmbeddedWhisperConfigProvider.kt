package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.os.Build
import com.lunatic.quicktranslate.feature.transcription.WhisperCliConfig
import java.io.File
import java.util.zip.ZipFile

class EmbeddedWhisperConfigProvider(
    private val context: Context
) {
    fun resolve(language: String, threads: Int): WhisperCliConfig {
        val cliFile = resolveExecutableCli()
        val modelFile = extractAssetIfNeeded(
            assetPath = "whisper/models/ggml-tiny.en.bin",
            output = File(context.filesDir, "whisper/models/ggml-tiny.en.bin")
        )
        return WhisperCliConfig(
            cliPath = cliFile.absolutePath,
            modelPath = modelFile.absolutePath,
            language = language.ifBlank { "en" },
            threads = threads
        )
    }

    private fun resolveExecutableCli(): File {
        val nativeCli = File(context.applicationInfo.nativeLibraryDir, "libwhisper_cli.so")
        if (nativeCli.exists()) {
            return nativeCli
        }
        return extractCliFromApk()
    }

    private fun extractCliFromApk(): File {
        val apkPath = context.applicationInfo.sourceDir
        val outputDir = File(context.filesDir, "whisper/bin")
        val outputFile = File(outputDir, "libwhisper_cli.so")
        outputDir.mkdirs()
        ZipFile(apkPath).use { zip ->
            val abi = Build.SUPPORTED_ABIS
                .asSequence()
                .firstOrNull { candidate ->
                    zip.getEntry("lib/$candidate/libwhisper_cli.so") != null
                }
                ?: error("Missing libwhisper_cli.so in apk for supported ABIs.")
            val libs = listOf(
                "libwhisper_cli.so",
                "libwhisper.so",
                "libggml.so",
                "libggml-cpu.so",
                "libggml-base.so"
            )
            libs.forEach { libName ->
                val out = File(outputDir, libName)
                if (out.exists() && out.length() > 0L) {
                    return@forEach
                }
                val entry = zip.getEntry("lib/$abi/$libName")
                    ?: error("Missing $libName in apk for ABI $abi.")
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { sink ->
                        input.copyTo(sink)
                    }
                }
            }
        }
        return outputFile
    }

    private fun extractAssetIfNeeded(
        assetPath: String,
        output: File
    ): File {
        if (output.exists() && output.length() > 0L) {
            return output
        }
        output.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            output.outputStream().use { sink ->
                input.copyTo(sink)
            }
        }
        return output
    }
}
