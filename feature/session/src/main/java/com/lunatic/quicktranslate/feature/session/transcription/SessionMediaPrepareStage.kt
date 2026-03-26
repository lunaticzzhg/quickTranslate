package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaFormat.KEY_CHANNEL_COUNT
import android.media.MediaFormat.KEY_MIME
import android.media.MediaFormat.KEY_PCM_ENCODING
import android.media.MediaFormat.KEY_SAMPLE_RATE
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedTranscriptionMedia(
    val path: String,
    val cleanup: (() -> Unit)? = null,
    val transcodedToWav: Boolean = false
)

class SessionMediaPrepareStage(
    private val appContext: Context
) {
    companion object {
        // Classic WAV (RIFF) uses 32-bit chunk sizes.
        private const val MAX_WAV_DATA_BYTES = 0xFFFF_FFFFL - 36L
        private val AUDIO_EXTENSIONS = setOf(
            "wav",
            "pcm",
            "mp3",
            "m4a",
            "aac",
            "flac",
            "ogg",
            "opus",
            "webm"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4",
            "m4v",
            "mov",
            "mkv",
            "3gp"
        )
    }

    suspend fun prepare(
        mediaUri: String,
        forceTranscodeToWav: Boolean = false
    ): PreparedTranscriptionMedia {
        return withContext(Dispatchers.IO) {
            prepareMediaPath(mediaUri, forceTranscodeToWav)
        }
    }

    private fun prepareMediaPath(
        mediaUri: String,
        forceTranscodeToWav: Boolean
    ): PreparedTranscriptionMedia {
        val uri = Uri.parse(mediaUri)
        val scheme = uri.scheme.orEmpty()
        val mimeType = resolveMimeType(uri)
        val needsAudioExtract = shouldExtractCompressedAudio(uri = uri, mimeType = mimeType)
        if (!forceTranscodeToWav && needsAudioExtract) {
            val extracted = runCatching { extractAudioTrackToM4a(uri = uri, fallbackPath = mediaUri) }
                .getOrNull()
            if (extracted != null) {
                return PreparedTranscriptionMedia(
                    path = extracted.absolutePath,
                    cleanup = { extracted.delete() },
                    transcodedToWav = false
                )
            }
        }
        if (forceTranscodeToWav || shouldTranscodeToWav(uri = uri, mimeType = mimeType)) {
            val wavFile = decodeAudioTrackToWav(uri = uri, fallbackPath = mediaUri)
            return PreparedTranscriptionMedia(
                path = wavFile.absolutePath,
                cleanup = { wavFile.delete() },
                transcodedToWav = true
            )
        }
        if (scheme.equals("content", ignoreCase = true)) {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("Unable to open media uri: $mediaUri")
            val file = File(appContext.cacheDir, "qt_transcribe_${UUID.randomUUID()}.media")
            input.use { source ->
                file.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
            return PreparedTranscriptionMedia(
                path = file.absolutePath,
                cleanup = { file.delete() },
                transcodedToWav = false
            )
        }
        if (scheme.equals("file", ignoreCase = true)) {
            return PreparedTranscriptionMedia(
                path = uri.path.orEmpty(),
                transcodedToWav = false
            )
        }
        return PreparedTranscriptionMedia(
            path = mediaUri,
            transcodedToWav = false
        )
    }

    private fun resolveMimeType(uri: Uri): String? {
        if (uri.scheme.equals("content", ignoreCase = true)) {
            return appContext.contentResolver.getType(uri)
        }
        return null
    }

    private fun shouldTranscodeToWav(uri: Uri, mimeType: String?): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val normalizedMime = mimeType?.lowercase().orEmpty()
        if (normalizedMime.startsWith("audio/")) {
            return false
        }
        if (normalizedMime.startsWith("video/")) {
            return false
        }
        val extension = path.substringAfterLast('.', "")
        if (extension in AUDIO_EXTENSIONS) {
            return false
        }
        if (extension in VIDEO_EXTENSIONS) {
            return true
        }
        return normalizedMime.isBlank()
    }

    private fun shouldExtractCompressedAudio(uri: Uri, mimeType: String?): Boolean {
        val path = uri.path.orEmpty().lowercase()
        val normalizedMime = mimeType?.lowercase().orEmpty()
        if (normalizedMime.startsWith("video/")) {
            return true
        }
        val extension = path.substringAfterLast('.', "")
        return extension in VIDEO_EXTENSIONS
    }

    private fun extractAudioTrackToM4a(uri: Uri, fallbackPath: String): File {
        val extractor = MediaExtractor()
        try {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                extractor.setDataSource(appContext, uri, null)
            } else if (uri.scheme.equals("file", ignoreCase = true)) {
                extractor.setDataSource(uri.path.orEmpty())
            } else {
                extractor.setDataSource(fallbackPath)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                val format = extractor.getTrackFormat(idx)
                format.getString(KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found for extraction.")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val outputFile = File(appContext.cacheDir, "qt_transcribe_${UUID.randomUUID()}.m4a")
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outputTrackIndex = muxer.addTrack(inputFormat)
                muxer.start()
                val bufferInfo = MediaCodec.BufferInfo()
                val sampleBuffer = ByteBuffer.allocate(1 * 1024 * 1024)
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(sampleBuffer, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    }
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(outputTrackIndex, sampleBuffer, bufferInfo)
                    extractor.advance()
                }
            } finally {
                runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            if (!outputFile.exists() || outputFile.length() <= 0L) {
                error("Audio extraction generated empty m4a.")
            }
            return outputFile
        } finally {
            extractor.release()
        }
    }

    private fun decodeAudioTrackToWav(uri: Uri, fallbackPath: String): File {
        val extractor = MediaExtractor()
        try {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                extractor.setDataSource(appContext, uri, null)
            } else if (uri.scheme.equals("file", ignoreCase = true)) {
                extractor.setDataSource(uri.path.orEmpty())
            } else {
                extractor.setDataSource(fallbackPath)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                val format = extractor.getTrackFormat(idx)
                format.getString(KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found for transcription.")
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(KEY_MIME) ?: error("Unsupported audio mime.")
            val codec = MediaCodec.createDecoderByType(mime)
            val outputFile = File(appContext.cacheDir, "qt_transcribe_${UUID.randomUUID()}.wav")
            FileOutputStream(outputFile).use { out ->
                out.write(ByteArray(44))
                var sampleRate = trackFormat.getInteger(KEY_SAMPLE_RATE)
                var channelCount = trackFormat.getInteger(KEY_CHANNEL_COUNT)
                var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                var dataBytes = 0L

                try {
                    codec.configure(trackFormat, null, null, 0)
                    codec.start()
                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputEos = false
                    var outputEos = false
                    while (!outputEos) {
                        if (!inputEos) {
                            val inputIndex = codec.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputIndex)
                                if (inputBuffer != null) {
                                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                    if (sampleSize < 0) {
                                        codec.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            0,
                                            0L,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        )
                                        inputEos = true
                                    } else {
                                        codec.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            sampleSize,
                                            extractor.sampleTime,
                                            0
                                        )
                                        extractor.advance()
                                    }
                                }
                            }
                        }
                        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                        when {
                            outputIndex >= 0 -> {
                                if (bufferInfo.size > 0) {
                                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                                    if (outputBuffer != null) {
                                        outputBuffer.position(bufferInfo.offset)
                                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                        val chunk = ByteArray(bufferInfo.size)
                                        outputBuffer.get(chunk)
                                        val pcmChunk = convertTo16BitPcm(
                                            sourceBytes = chunk,
                                            encoding = pcmEncoding
                                        )
                                        out.write(pcmChunk)
                                        dataBytes += pcmChunk.size
                                        if (dataBytes > MAX_WAV_DATA_BYTES) {
                                            throw IllegalStateException(
                                                "Audio is too long for WAV output. Please trim the media and retry."
                                            )
                                        }
                                    }
                                }
                                codec.releaseOutputBuffer(outputIndex, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputEos = true
                                }
                            }
                            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val outFormat = codec.outputFormat
                                sampleRate = outFormat.getInteger(KEY_SAMPLE_RATE)
                                channelCount = outFormat.getInteger(KEY_CHANNEL_COUNT)
                                pcmEncoding = outFormat.getIntegerOrNull(KEY_PCM_ENCODING)
                                    ?: AudioFormat.ENCODING_PCM_16BIT
                            }
                        }
                    }
                } finally {
                    runCatching { codec.stop() }
                    runCatching { codec.release() }
                }
                patchWaveHeader(
                    file = outputFile,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    bitsPerSample = 16,
                    dataSize = dataBytes
                )
            }
            return outputFile
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private fun convertTo16BitPcm(sourceBytes: ByteArray, encoding: Int): ByteArray {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> sourceBytes
            AudioFormat.ENCODING_PCM_FLOAT -> convertFloatPcmTo16Bit(sourceBytes)
            AudioFormat.ENCODING_PCM_8BIT -> convert8BitPcmTo16Bit(sourceBytes)
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> convert24BitPcmTo16Bit(sourceBytes)
            AudioFormat.ENCODING_PCM_32BIT -> convert32BitPcmTo16Bit(sourceBytes)
            else -> throw IllegalStateException("Unsupported PCM encoding: $encoding")
        }
    }

    private fun convertFloatPcmTo16Bit(floatBytes: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(floatBytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteBuffer.allocate((floatBytes.size / 4) * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 4) {
            val value = input.float.coerceIn(-1f, 1f)
            val pcm = (value * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out.putShort(pcm.toShort())
        }
        return out.array()
    }

    private fun convert8BitPcmTo16Bit(bytes: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(bytes.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.forEach { raw ->
            // 8-bit PCM is unsigned in Android decode output.
            val centered = (raw.toInt() and 0xFF) - 128
            out.putShort((centered shl 8).toShort())
        }
        return out.array()
    }

    private fun convert24BitPcmTo16Bit(bytes: ByteArray): ByteArray {
        val frameCount = bytes.size / 3
        val out = ByteBuffer.allocate(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        var index = 0
        repeat(frameCount) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = bytes[index + 1].toInt() and 0xFF
            val b2 = bytes[index + 2].toInt()
            var sample = b0 or (b1 shl 8) or (b2 shl 16)
            if (sample and 0x0080_0000 != 0) {
                sample = sample or -0x0100_0000
            }
            out.putShort((sample shr 8).toShort())
            index += 3
        }
        return out.array()
    }

    private fun convert32BitPcmTo16Bit(bytes: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteBuffer.allocate((bytes.size / 4) * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 4) {
            val sample = input.int
            out.putShort((sample shr 16).toShort())
        }
        return out.array()
    }

    private fun patchWaveHeader(
        file: File,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int,
        dataSize: Long
    ) {
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        RandomAccessFile(file, "rw").use { raf ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt((36 + dataSize).toInt())
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(channelCount.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize.toInt())
            raf.seek(0L)
            raf.write(header.array())
        }
    }
}
