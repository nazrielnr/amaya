package com.amaya.intelligence.ui.activities.project.local

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.amaya.intelligence.ui.PermissionRequestScreen
import com.amaya.intelligence.ui.screens.project.local.LocalProjectBrowserScreen
import com.amaya.intelligence.ui.theme.AmayaTheme

class LocalProjectActivity : AppCompatActivity() {

    private var hasStoragePermission by mutableStateOf(false)

    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                hasStoragePermission = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkStoragePermission()
        setContent {
            AmayaTheme {
                if (hasStoragePermission) {
                    LocalProjectBrowserScreen(
                        onWorkspaceSelected = { workspacePath ->
                            setResult(RESULT_OK, Intent().putExtra(RESULT_KEY, workspacePath))
                            finish()
                        },
                        onDismiss = {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                } else {
                    PermissionRequestScreen(onRequestPermission = { requestStoragePermission() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    companion object {
        const val REQUEST_CODE = 1001
        const val RESULT_KEY = "workspace_path"

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, LocalProjectActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun startForResult(activity: android.app.Activity) {
            val intent = android.content.Intent(activity, LocalProjectActivity::class.java)
            activity.startActivityForResult(intent, REQUEST_CODE)
        }
    }
}
