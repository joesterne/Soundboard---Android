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

    suspend fun extractWaveformAmplitudes(path: String, samplePoints: Int = 60): List<Float> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        val amplitudes = mutableListOf<Float>()
        try {
            val file = java.io.File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) return@withContext emptyList()

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
            if (format == null) return@withContext emptyList()
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val rawSamples = mutableListOf<Float>()
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

                        val byteBuffer = outBuffer.slice()
                        byteBuffer.order(java.nio.ByteOrder.nativeOrder())
                        var peak = 0f
                        while (byteBuffer.remaining() >= 2) {
                            val sample = Math.abs(byteBuffer.short.toFloat() / Short.MAX_VALUE)
                            if (sample > peak) peak = sample
                        }
                        rawSamples.add(peak)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(info, 5000)
                }
            }

            if (rawSamples.isEmpty()) return@withContext emptyList()

            // Downsample rawSamples into the desired number of buckets
            val bucketSize = (rawSamples.size.toFloat() / samplePoints).coerceAtLeast(1f)
            var maxBucketPeak = 0.01f
            val buckets = mutableListOf<Float>()
            for (i in 0 until samplePoints) {
                val startIdx = (i * bucketSize).toInt().coerceIn(0, rawSamples.size - 1)
                val endIdx = ((i + 1) * bucketSize).toInt().coerceIn(startIdx + 1, rawSamples.size)
                var bucketMax = 0f
                for (j in startIdx until endIdx) {
                    if (rawSamples[j] > bucketMax) bucketMax = rawSamples[j]
                }
                buckets.add(bucketMax)
                if (bucketMax > maxBucketPeak) maxBucketPeak = bucketMax
            }

            // Normalize between 0.15f and 1.0f for crisp visual presentation
            buckets.map { (it / maxBucketPeak).coerceIn(0.12f, 1.0f) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            try { codec?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }
}
