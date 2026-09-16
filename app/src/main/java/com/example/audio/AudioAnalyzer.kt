package com.example.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioAnalyzer {
    suspend fun calculateNormalizationVolume(path: String): Float = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(path)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            
            if (format == null) return@withContext 1.0f
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext 1.0f
            
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            
            var maxAmplitude = 0.0f
            val info = MediaCodec.BufferInfo()
            var isEOS = false
            
            while (!isEOS) {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)
                    if (buffer != null) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                var outIndex = codec.dequeueOutputBuffer(info, 5000)
                while (outIndex >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                    }
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && info.size > 0) {
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        
                        // Parse PCM samples
                        val byteBuffer = outBuffer.slice()
                        byteBuffer.order(java.nio.ByteOrder.nativeOrder())
                        while (byteBuffer.remaining() >= 2) {
                            val sample = Math.abs(byteBuffer.short.toFloat() / Short.MAX_VALUE)
                            if (sample > maxAmplitude) maxAmplitude = sample
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(info, 5000)
                }
            }
            
            if (maxAmplitude > 0.0f) {
                // Clamp max multiplier to 10.0x (1000%) to avoid insane boosts on near silence
                return@withContext (1.0f / maxAmplitude).coerceAtMost(10.0f)
            }
            return@withContext 1.0f
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 1.0f
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            try { codec?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }
}
