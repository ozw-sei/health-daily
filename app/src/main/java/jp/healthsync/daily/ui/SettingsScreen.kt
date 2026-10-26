package jp.healthsync.daily.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import jp.healthsync.daily.health.HealthAvailability
import jp.healthsync.daily.settings.SendLogEntry
import jp.healthsync.daily.summary.TOKYO
import jp.healthsync.daily.summary.describeNextSync

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onRequestPermissions: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.banner) {
        val message = state.banner ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissBanner()
    }
    HealthDailyTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            SettingsScreen(
                state = state,
                onWebhookChange = viewModel::onWebhookChange,
                onHeaderNameChange = viewModel::onHeaderNameChange,
                onHeaderValueChange = viewModel::onHeaderValueChange,
                onSave = viewModel::save,
                onSendToday = viewModel::sendToday,
                onSendYesterday = viewModel::sendYesterday,
                onSendWeek = viewModel::sendLast7Days,
                onRequestPermissions = onRequestPermissions,
                onInstallHealthConnect = onInstallHealthConnect,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onWebhookChange: (String) -> Unit,
    onHeaderNameChange: (String) -> Unit,
    onHeaderValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onSendToday: () -> Unit,
    onSendYesterday: () -> Unit,
    onSendWeek: () -> Unit,
    onRequestPermissions: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("日次ヘルス", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Fitbit、Pixel Watch、asken、MyFitnessPal、EufyLife が Health Connect に書いた今日の記録を、1日1回 JSON で webhook に送ります。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthCard(state, onRequestPermissions, onInstallHealthConnect)
            WebhookCard(
                state = state,
                onWebhookChange = onWebhookChange,
                onHeaderNameChange = onHeaderNameChange,
                onHeaderValueChange = onHeaderValueChange,
                onSave = onSave,
            )
            SendCard(
                sending = state.sending,
                onSendToday = onSendToday,
                onSendYesterday = onSendYesterday,
                onSendWeek = onSendWeek,
            )
            LogCard(state.logs)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HealthCard(
    state: SettingsUiState,
    onRequestPermissions: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    SectionCard(title = "Health Connect") {
        Text(availabilityText(state.availability), style = MaterialTheme.typography.bodyLarge)
        Text(
            "読み取り許可 ${state.grantedCount} / ${state.requestedCount}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            backgroundText(state.backgroundGranted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "許可されなかった種類は送らず、JSON では null になります。21:00 の自動送信にはバックグラウンド読み取りが必要です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.availability == HealthAvailability.Available) {
            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("権限をリクエスト")
            }
        } else if (state.availability == HealthAvailability.UpdateRequired) {
            Button(onClick = onInstallHealthConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Health Connect をインストールまたは更新")
            }
        }
    }
}

@Composable
private fun WebhookCard(
    state: SettingsUiState,
    onWebhookChange: (String) -> Unit,
    onHeaderNameChange: (String) -> Unit,
    onHeaderValueChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    SectionCard(title = "送信先") {
        OutlinedTextField(
            value = state.webhookUrl,
            onValueChange = onWebhookChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Webhook URL") },
            placeholder = { Text("https://example.com/hooks/health") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = state.authHeaderName,
            onValueChange = onHeaderNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("認証ヘッダー名") },
            supportingText = {
                Text("初期値は Authorization です。受信側が別の名前を指定したときだけ変えてください。")
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.authHeaderValue,
            onValueChange = onHeaderValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("認証ヘッダー値") },
            placeholder = { Text("受信側が渡した文字列をそのまま貼り付け") },
            supportingText = {
                Text("Bearer などの接頭辞も含め、渡された値を加工せずに送ります。空ならヘッダー自体を付けません。端末内では暗号化して保存します。")
            },
            singleLine = true,
            visualTransformation = if (reveal) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "隠す" else "表示")
                }
            },
        )
        Button(onClick = onSave, enabled = !state.sending, modifier = Modifier.fillMaxWidth()) {
            Text("設定を保存")
        }
    }
}

@Composable
private fun SendCard(
    sending: Boolean,
    onSendToday: () -> Unit,
    onSendYesterday: () -> Unit,
    onSendWeek: () -> Unit,
) {
    SectionCard(title = "送信") {
        Text(
            "自動送信は ${describeNextSync(java.time.ZonedDateTime.now(TOKYO))} 頃です。圏外のときは接続後に送られ、サーバーエラーは間隔を空けて再試行します。複数日も1回の POST にまとめます。ボタンは保存してから送ります。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = onSendToday, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
            Text("今日を今すぐ送信")
        }
        OutlinedButton(onClick = onSendYesterday, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
            Text("昨日を送信")
        }
        OutlinedButton(onClick = onSendWeek, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
            Text("過去7日を送信")
        }
    }
}

@Composable
private fun LogCard(logs: List<SendLogEntry>) {
    SectionCard(title = "最近の送信") {
        if (logs.isEmpty()) {
            Text(
                "まだ送信履歴がありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun LogRow(entry: SendLogEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(formatLogTime(entry.at), style = MaterialTheme.typography.labelLarge)
            Text(
                statusLabel(entry),
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            entry.detail,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun availabilityText(availability: HealthAvailability): String = when (availability) {
    HealthAvailability.Available -> "Health Connect は利用できます"
    HealthAvailability.UpdateRequired -> "Health Connect のインストールまたは更新が必要です"
    HealthAvailability.Unavailable -> "この端末では Health Connect を利用できません"
}

private fun backgroundText(granted: Boolean?): String = when (granted) {
    true -> "バックグラウンド読み取り: 許可済み"
    false -> "バックグラウンド読み取り: 未許可"
    null -> "バックグラウンド読み取り: この端末では確認できません"
}

private fun statusLabel(entry: SendLogEntry): String {
    val code = entry.status?.toString() ?: "—"
    return if (entry.ok) "HTTP $code" else "失敗 $code"
}

private fun formatLogTime(iso: String): String {
    return try {
        val time = OffsetDateTime.parse(iso).atZoneSameInstant(TOKYO)
        DateTimeFormatter.ofPattern("MM/dd HH:mm").format(time)
    } catch (_: Exception) {
        iso
    }
}
