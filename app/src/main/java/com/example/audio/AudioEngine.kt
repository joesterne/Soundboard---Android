package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()
    private val activeStreams = mutableMapOf<Int, Int>()
    
    private val activeMediaPlayers = mutableMapOf<Int, MediaPlayer>()
    private val stopRunnables = mutableMapOf<Int, Runnable>()
    private val activeEnhancers = mutableMapOf<Int, LoudnessEnhancer>()
    private val handler = Handler(Looper.getMainLooper())

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // To be heard by callers
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun loadSound(path: String) {
        if (!soundMap.containsKey(path) && File(path).exists()) {
            val soundId = soundPool.load(path, 1)
            soundMap[path] = soundId
        }
    }

    fun playSound(
        id: Int, 
        path: String, 
        volume: Float = 1.0f, 
        isLooping: Boolean = false,
        trimStartMs: Long? = null,
        trimEndMs: Long? = null
    ) {
        stopSound(id)

        if (trimStartMs != null || trimEndMs != null || volume > 1.0f) {
            playWithMediaPlayer(id, path, volume, isLooping, trimStartMs, trimEndMs)
            return
        }

        val loopMode = if (isLooping) -1 else 0
        
        val soundId = soundMap[path]
        if (soundId != null) {
            val streamId = soundPool.play(soundId, volume, volume, 1, loopMode, 1f)
            if (isLooping && streamId != 0) {
                activeStreams[id] = streamId
            }
        } else {
            // Try loading it on the fly if not loaded yet
            if (File(path).exists()) {
                val newId = soundPool.load(path, 1)
                soundMap[path] = newId
                soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
                    if (status == 0 && sampleId == newId) {
                        val streamId = pool.play(sampleId, volume, volume, 1, loopMode, 1f)
                        if (isLooping && streamId != 0) {
                            activeStreams[id] = streamId
                        }
                    }
                }
            }
        }
    }

    private fun playWithMediaPlayer(id: Int, path: String, volume: Float, isLooping: Boolean, startMs: Long?, endMs: Long?) {
        try {
            if (!File(path).exists()) return
            
            val mp = MediaPlayer()
            mp.setDataSource(path)
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mp.setAudioAttributes(audioAttributes)
            
            if (volume > 1.0f) {
                mp.setVolume(1.0f, 1.0f)
                try {
                    val enhancer = LoudnessEnhancer(mp.audioSessionId)
                    val gainmB = (Math.log10(volume.toDouble()) * 2000.0).toInt()
                    enhancer.setTargetGain(gainmB)
                    enhancer.enabled = true
                    activeEnhancers[id] = enhancer
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                mp.setVolume(volume, volume)
            }
            
            mp.prepare()
            
            val actualStartMs = startMs ?: 0L
            if (actualStartMs > 0) {
                mp.seekTo(actualStartMs.toInt())
            }
            
            if (isLooping) {
                if (endMs != null) {
                    val durationToPlay = endMs - actualStartMs
                    if (durationToPlay > 0) {
                        val loopRunnable = object : Runnable {
                            override fun run() {
                                if (activeMediaPlayers[id] == mp) {
                                    mp.seekTo(actualStartMs.toInt())
                                    mp.start()
                                    handler.postDelayed(this, durationToPlay)
                                }
                            }
                        }
                        mp.start()
                        handler.postDelayed(loopRunnable, durationToPlay)
                        stopRunnables[id] = loopRunnable
                    }
                } else {
                    mp.isLooping = true
                    mp.start()
                }
            } else {
                if (endMs != null) {
                    val durationToPlay = endMs - actualStartMs
                    if (durationToPlay > 0) {
                        val stopRunnable = Runnable {
                            if (activeMediaPlayers[id] == mp) {
                                mp.stop()
                                mp.release()
                                activeMediaPlayers.remove(id)
                                stopRunnables.remove(id)
                            }
                        }
                        handler.postDelayed(stopRunnable, durationToPlay)
                        stopRunnables[id] = stopRunnable
                    }
                }
                mp.setOnCompletionListener {
                    it.release()
                    activeMediaPlayers.remove(id)
                }
                mp.start()
            }
            
            activeMediaPlayers[id] = mp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSound(id: Int) {
        val streamId = activeStreams.remove(id)
        if (streamId != null) {
            soundPool.stop(streamId)
        }
        
        val mp = activeMediaPlayers.remove(id)
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val enhancer = activeEnhancers.remove(id)
        if (enhancer != null) {
            try {
                enhancer.enabled = false
                enhancer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val runnable = stopRunnables.remove(id)
        if (runnable != null) {
            handler.removeCallbacks(runnable)
        }
    }
    
    fun isLoopingActive(id: Int): Boolean {
        return activeStreams.containsKey(id) || activeMediaPlayers.containsKey(id)
    }

    fun release() {
        soundPool.release()
        soundMap.clear()
        activeStreams.clear()
        
        activeMediaPlayers.values.forEach {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeMediaPlayers.clear()
        
        activeEnhancers.values.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeEnhancers.clear()
        
        stopRunnables.values.forEach { handler.removeCallbacks(it) }
        stopRunnables.clear()
    }
}
