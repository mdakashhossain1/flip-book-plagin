package com.scoreplus.flipbook.internal

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.scoreplus.flipbook.R

/** spflip.controls.sound: flip-sm on corner grab, flip-md / flip-lg while turning. */
internal class FlipSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val small = pool.load(context, R.raw.flipbook_flip_sm, 1)
    private val medium = pool.load(context, R.raw.flipbook_flip_md, 1)
    private val large = pool.load(context, R.raw.flipbook_flip_lg, 1)
    private val streams = IntArray(3)

    var enabled = true
    var pageTurnSound = true

    fun peelStart() {
        if (enabled && pageTurnSound) play(0, small, 0.1f)
    }

    fun turning(page: Int?, visible: IntArray) {
        if (!enabled || !pageTurnSound || page == null) return
        val far = page < visible[0] - 1 ||
            (visible.size == 2 && page > visible[1] + 1) ||
            (visible.size == 1 && page > visible[0] + 1)
        if (far) play(2, large, 0.2f) else play(1, medium, 0.2f)
    }

    private fun play(slot: Int, id: Int, volume: Float) {
        if (streams[slot] != 0) pool.stop(streams[slot])
        streams[slot] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
