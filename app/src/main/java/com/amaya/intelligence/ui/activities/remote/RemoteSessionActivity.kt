package com.amaya.intelligence.ui.activities.remote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteSessionClient
import com.amaya.intelligence.ui.screens.remote.RemoteSessionScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RemoteSessionActivity : AppCompatActivity() {

    @Inject
    lateinit var remoteSessionClient: RemoteSessionClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AmayaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RemoteSessionScreen(
                        client = remoteSessionClient,
                        onBack = { finish() },
                        onConnected = {
                            RemoteChatActivity.start(this@RemoteSessionActivity)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RemoteSessionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
