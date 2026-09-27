package com.scoreplus.flipbook.sample

import android.app.Activity
import android.os.Bundle
import com.scoreplus.flipbook.FlipbookView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FlipbookView(this))
    }
}
