package com.shinjikai.dictionary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LatinDigits.wrap(newBase))
    }

    private var processTextQuery by mutableStateOf<String?>(null)
    private var externalDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIncomingIntent(intent)
        setContent {
            ShinjikaiApp(
                externalSearchTerm = processTextQuery,
                externalDeepLink = externalDeepLink,
                onExternalSearchTermConsumed = { processTextQuery = null },
                onExternalDeepLinkConsumed = { externalDeepLink = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIncomingIntent(intent)
    }

    private fun consumeIncomingIntent(intent: Intent?) {
        processTextQuery = extractProcessTextQuery(intent)
        externalDeepLink = extractDeepLink(intent)
        setIntent(
            if (processTextQuery != null || externalDeepLink != null) {
                Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            } else {
                intent ?: Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            }
        )
    }

    private fun extractProcessTextQuery(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractDeepLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.dataString?.takeIf { it.isNotBlank() }
    }
}
