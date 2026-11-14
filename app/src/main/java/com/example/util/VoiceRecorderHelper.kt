package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        stopSilently()
        return try {
            val file = File(context.cacheDir, "nexo_voice_cmd_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la grabación de audio", e)
            stopSilently()
            false
        }
    }

    fun stopAndReadBytes(): Pair<ByteArray, String>? {
        val mr = recorder ?: return null
        val file = outputFile
        recorder = null
        return try {
            mr.stop()
            mr.release()
            if (file != null && file.exists() && file.length() > 0L && file.length() <= MAX_INLINE_BYTES) {
                val bytes = file.readBytes()
                file.delete()
                bytes to "audio/mp4"
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo grabación de audio", e)
            try {
                mr.release()
            } catch (_: Exception) {
            }
            file?.delete()
            null
        } finally {
            outputFile = null
        }
    }

    fun stopSilently() {
        val mr = recorder
        recorder = null
        if (mr != null) {
            try {
                mr.stop()
            } catch (_: Exception) {
            }
            try {
                mr.release()
            } catch (_: Exception) {
            }
        }
        outputFile?.delete()
        outputFile = null
    }

    companion object {
        private const val TAG = "VoiceRecorderHelper"
        const val MAX_INLINE_BYTES = 18 * 1024 * 1024L

        fun readAudioUriBytes(context: Context, uri: Uri, fallbackMime: String?): Pair<ByteArray, String>? {
            return try {
                val resolvedMime = context.contentResolver.getType(uri) ?: fallbackMime ?: "audio/ogg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size <= MAX_INLINE_BYTES) data else null
                } ?: return null
                bytes to resolvedMime
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo URI de audio compartido", e)
                null
            }
        }
    }
}
