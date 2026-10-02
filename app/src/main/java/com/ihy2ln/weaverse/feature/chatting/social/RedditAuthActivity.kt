package com.ihy2ln.weaverse.feature.chatting.social

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import com.ihy2ln.weaverse.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Catches Reddit's sign-in redirect (weaverse://reddit-auth), finishes sign-in, and returns to the app. */
@AndroidEntryPoint
class RedditAuthActivity : ComponentActivity() {
    @Inject lateinit var reddit: RedditAccount

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data
        if (redirect == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val result = reddit.complete(redirect)
            Toast.makeText(
                this@RedditAuthActivity,
                result.fold({ "Reddit connected as u/$it" }, { it.message ?: "Reddit sign-in failed" }),
                Toast.LENGTH_LONG,
            ).show()
            startActivity(Intent(this@RedditAuthActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
