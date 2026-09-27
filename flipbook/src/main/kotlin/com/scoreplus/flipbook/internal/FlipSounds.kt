package com.scoreplus.flipbook.internal

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import com.scoreplus.flipbook.R

/** spflip.controls.sound: flip-sm on corner grab, flip-md / flip-lg while turning. */
internal class FlipSounds(context: Context) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val player: Player =
        if (Build.VERSION.SDK_INT >= 34) ContextPlayers(context, attributes) else PoolPlayer(context, attributes)

    var enabled = true
    var pageTurnSound = true

    /** Like the web version, stays silent until the reader has interacted (web: navigator.userActivation). */
    var userActivated = false

    fun peelStart() {
        if (enabled && pageTurnSound && userActivated) player.play(SMALL, 0.1f)
    }

    fun turning(page: Int?, visible: IntArray) {
        if (!enabled || !pageTurnSound || !userActivated || page == null) return
        val far = page < visible[0] - 1 ||
            (visible.size == 2 && page > visible[1] + 1) ||
            (visible.size == 1 && page > visible[0] + 1)
        player.play(if (far) LARGE else MEDIUM, 0.2f)
    }

    fun release() = player.release()

    private interface Player {
        fun play(sound: Int, volume: Float)
        fun release()
    }

    /**
     * Android 14+: plays through a context tagged with the "flipbook" attribution declared in the
     * library manifest; untagged playback makes system_server log an AppOps
     * "attributionTag not declared" error on every sound.
     */
    private class ContextPlayers(context: Context, attributes: AudioAttributes) : Player {
        private val audioContext = context.createAttributionContext("flipbook")
        private val players = RAW.map { res ->
            MediaPlayer(audioContext).apply {
                setAudioAttributes(attributes)
                context.resources.openRawResourceFd(res).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                prepare()
            }
        }

        override fun play(sound: Int, volume: Float) {
            val p = players[sound]
            if (p.isPlaying) p.pause()
            p.seekTo(0)
            p.setVolume(volume, volume)
            p.start()
        }

        override fun release() = players.forEach { it.release() }
    }

    private class PoolPlayer(context: Context, attributes: AudioAttributes) : Player {
        private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        private val ids = RAW.map { pool.load(context, it, 1) }
        private val streams = IntArray(3)

        override fun play(sound: Int, volume: Float) {
            if (streams[sound] != 0) pool.stop(streams[sound])
            streams[sound] = pool.play(ids[sound], volume, volume, 1, 0, 1f)
        }

        override fun release() = pool.release()
    }

    private companion object {
        const val SMALL = 0
        const val MEDIUM = 1
        const val LARGE = 2
        val RAW = listOf(R.raw.flipbook_flip_sm, R.raw.flipbook_flip_md, R.raw.flipbook_flip_lg)
    }
}
