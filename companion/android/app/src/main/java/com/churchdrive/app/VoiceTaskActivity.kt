package com.churchdrive.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * "Add a task by voice": opens the phone's own speech box, listens once when asked (never in the background), then passes
 * what was said to the app, which sends it to the house to add straight to the lists. Reached from the microphone in the
 * app, the Quick Settings tile, and the app icon's long-press menu. It has no screen of its own.
 */
class VoiceTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listen = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What needs doing?")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(listen, SPEECH)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "This phone has no speech recogniser", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("The speech box answers through the old result call")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (requestCode == SPEECH && resultCode == RESULT_OK && !said.isNullOrEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHARE_TEXT, said)
                    .putExtra(MainActivity.EXTRA_SHARE_SOURCE, "Voice")
                    .putExtra(MainActivity.EXTRA_SHARE_AUTO_ADD, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }

    private companion object {
        const val SPEECH = 41
    }
}
