package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.security.configureAppCheck
import com.example.ui.NusaKasApp

class MainActivity : FragmentActivity() {
    private var deepLinkTab by mutableStateOf<String?>(null)
    private var deepLinkSubTab by mutableStateOf<Int?>(null)
    private var deepLinkNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            NusaKasApp(
                deepLinkTab = deepLinkTab,
                deepLinkSubTab = deepLinkSubTab,
                deepLinkNonce = deepLinkNonce
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        deepLinkTab = tab
        deepLinkSubTab = if (intent.hasExtra(EXTRA_OPEN_SUBTAB)) {
            intent.getIntExtra(EXTRA_OPEN_SUBTAB, 0)
        } else null
        deepLinkNonce++
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_SUBTAB = "open_subtab"
    }
}

