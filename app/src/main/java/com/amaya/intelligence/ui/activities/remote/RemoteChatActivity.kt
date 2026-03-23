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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteSessionClient
import com.amaya.intelligence.ui.screens.chat.shared.ChatScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RemoteChatActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AmayaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mainViewModel: com.amaya.intelligence.ui.viewmodels.ChatViewModel = hiltViewModel()

                    LaunchedEffect(Unit) {
                        mainViewModel.switchMode(IntelligenceSessionManager.SessionMode.ANTIGRAVITY)
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            mainViewModel.switchMode(IntelligenceSessionManager.SessionMode.LOCAL)
                        }
                    }

                    ChatScreen(
                        viewModel = mainViewModel,
                        config = com.amaya.intelligence.ui.screens.chat.shared.remoteChatScreenConfig(
                            onExit = { finish() },
                            onNavigateToSettings = {},
                            onToolAccept = { execution -> mainViewModel.respondToToolInteraction(execution.toolCallId, true) },
                            onToolDecline = { execution -> mainViewModel.respondToToolInteraction(execution.toolCallId, false) }
                        ),
                        onNavigateToWorkspace = {
                            RemoteProjectActivity.start(this@RemoteChatActivity)
                        },
                        onExit = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RemoteChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
