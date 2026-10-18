package jp.healthsync.daily

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import jp.healthsync.daily.ui.SettingsRoute
import jp.healthsync.daily.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsRoute(
                viewModel = viewModel,
                onRequestPermissions = {
                    val permissions = viewModel.permissionsToRequest()
                    if (permissions.isEmpty()) {
                        openHealthConnect()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                },
                onInstallHealthConnect = { openHealthConnect() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHealth()
    }

    private fun openHealthConnect() {
        val market = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding",
            )
            setPackage("com.android.vending")
        }
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
                ),
            )
        }
    }
}
