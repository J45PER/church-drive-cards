package com.churchdrive.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.churchdrive.app.ui.sharedText

/**
 * The "Church Drive" choice in the share sheet (Share > Church Drive) and in the menu of highlighted text (Church Drive
 * to-do). It has no screen of its own: it passes the text to the app, which has the sign-in and shows the tasks to check.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = sharedText(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
        )
        if (text != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHARE_TEXT, text)
                    .putExtra(MainActivity.EXTRA_SHARE_SOURCE, if (intent.action == Intent.ACTION_PROCESS_TEXT) "Highlight" else "Share")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
