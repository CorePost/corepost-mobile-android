package com.example.corepostemergencybutton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.corepostemergencybutton.data.CorePostConfig
import com.example.corepostemergencybutton.data.DashboardModel
import com.example.corepostemergencybutton.data.DashboardTone
import com.example.corepostemergencybutton.data.MobileStatus
import com.example.corepostemergencybutton.data.displayLabel
import com.example.corepostemergencybutton.data.maskMiddle
import com.example.corepostemergencybutton.data.toDashboardModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

@Composable
fun CorePostApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val countdownSeconds = rememberCountdownSeconds(uiState.pendingLockDeadlineMs)

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    LaunchedEffect(countdownSeconds, uiState.pendingLockDeadlineMs) {
        if (countdownSeconds == null && uiState.pendingLockDeadlineMs != null) {
            viewModel.onPendingWindowExpired()
        }
    }

    val editingMode = uiState.isEditingSettings || !uiState.hasSavedConfig
    val dashboardModel = uiState.mobileStatus?.toDashboardModel(
        isAwaitingSecondTap = countdownSeconds != null,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CorePostTopBar(
                editingMode = editingMode,
                hasSavedConfig = uiState.hasSavedConfig,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshStatus(showSpinner = true) },
                onOpenSettings = viewModel::openSettings,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(innerPadding),
        ) {
            if (uiState.isRefreshing || uiState.isPerformingAction || uiState.isSavingSettings) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedContent(
                targetState = editingMode,
                label = "corepost-screen",
                modifier = Modifier.fillMaxSize(),
            ) { showSettings ->
                if (showSettings) {
                    SettingsScreen(
                        uiState = uiState,
                        onBaseUrlChanged = viewModel::onBaseUrlChanged,
                        onEmergencyIdChanged = viewModel::onEmergencyIdChanged,
                        onPanicSecretChanged = viewModel::onPanicSecretChanged,
                        onSave = viewModel::saveSettings,
                        onCancel = viewModel::cancelSettings,
                        onClear = viewModel::clearSettings,
                    )
                } else {
                    DashboardScreen(
                        status = uiState.mobileStatus,
                        dashboardModel = dashboardModel,
                        config = uiState.savedConfig,
                        inlineError = uiState.inlineError,
                        isBusy = uiState.isRefreshing || uiState.isPerformingAction,
                        lastSyncMs = uiState.lastSuccessfulSyncMs,
                        countdownSeconds = countdownSeconds,
                        onPrimaryAction = viewModel::triggerPrimaryAction,
                        onRefresh = { viewModel.refreshStatus(showSpinner = true) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorePostTopBar(
    editingMode: Boolean,
    hasSavedConfig: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "CorePost Panic Client",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (editingMode) "Защищенная настройка подключения" else "Мгновенная реакция на инцидент",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            if (hasSavedConfig && !editingMode) {
                IconButton(onClick = onRefresh) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить статус")
                    }
                }
            }
            if (hasSavedConfig) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
        windowInsets = WindowInsets.statusBars,
    )
}

@Composable
private fun SettingsScreen(
    uiState: MainUiState,
    onBaseUrlChanged: (String) -> Unit,
    onEmergencyIdChanged: (String) -> Unit,
    onPanicSecretChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    var showSecret by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                icon = Icons.Rounded.Security,
                title = "Конфигурация хранится только на устройстве",
                body = "Base URL, emergency ID и panic secret вводятся пользователем, редактируются в любой момент и могут быть полностью удалены из защищенного хранилища.",
            )
        }
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Параметры panic-доступа",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Настройки сохраняются в EncryptedSharedPreferences и используются для HMAC-подписей `METHOD\\nPATH\\nTIMESTAMP`.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.baseUrlInput,
                        onValueChange = onBaseUrlChanged,
                        label = { Text("Base URL") },
                        placeholder = { Text("https://server.example") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.emergencyIdInput,
                        onValueChange = onEmergencyIdChanged,
                        label = { Text("Emergency ID") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.panicSecretInput,
                        onValueChange = onPanicSecretChanged,
                        label = { Text("Panic secret") },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showSecret) "Скрыть секрет" else "Показать секрет",
                                )
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.inlineError?.let { error ->
                        InlineError(error = error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PrimaryPillButton(
                            label = if (uiState.isSavingSettings) "Сохраняю..." else "Сохранить и проверить",
                            enabled = !uiState.isSavingSettings,
                            modifier = Modifier.weight(1f),
                            onClick = onSave,
                        )
                        if (uiState.hasSavedConfig) {
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                    if (uiState.hasSavedConfig) {
                        TextButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Очистить сохраненные настройки")
                        }
                    }
                }
            }
        }
        item {
            SupportingChecklistCard(
                title = "Demo flow",
                lines = listOf(
                    "1. Запустить `emulator -avd my_avd`.",
                    "2. Если сервер слушает на хосте и используется локальный адрес хоста, выполнить `adb reverse tcp:<PORT> tcp:<PORT>` для выбранного порта.",
                    "3. При необходимости зарегистрировать тестовое устройство через `scripts/provision_demo_device.py`.",
                    "4. Вставить `emergencyId` и `panicSecret`, затем проверить status/lock/unlock.",
                ),
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    status: MobileStatus?,
    dashboardModel: DashboardModel?,
    config: CorePostConfig?,
    inlineError: String?,
    isBusy: Boolean,
    lastSyncMs: Long?,
    countdownSeconds: Int?,
    onPrimaryAction: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatusHeroCard(
                status = status,
                dashboardModel = dashboardModel,
                lastSyncMs = lastSyncMs,
            )
        }
        item {
            PanicActionCard(
                dashboardModel = dashboardModel,
                countdownSeconds = countdownSeconds,
                isBusy = isBusy,
                onPrimaryAction = onPrimaryAction,
            )
        }
        if (inlineError != null) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Связь с сервером нарушена",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = inlineError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Повторить запрос")
                        }
                    }
                }
            }
        }
        if (config != null && status != null) {
            item {
                ConnectionCard(
                    baseUrl = config.normalizedBaseUrl,
                    emergencyId = config.emergencyId,
                    deviceId = status.deviceId,
                )
            }
        }
        item {
            SupportingChecklistCard(
                title = "Что показать на демо",
                lines = listOf(
                    "Старт экрана в `registered` или `normal`.",
                    "Первый tap: переход в `pending_lock` и обратный отсчет.",
                    "Второй tap: переход в `locked`.",
                    "Unlock: возврат в `recovered` при `userCanUnlock=true`.",
                ),
            )
        }
    }
}

@Composable
private fun StatusHeroCard(
    status: MobileStatus?,
    dashboardModel: DashboardModel?,
    lastSyncMs: Long?,
) {
    val brush = when (dashboardModel?.tone) {
        DashboardTone.DANGER -> Brush.linearGradient(listOf(Color(0xFF7A2313), Color(0xFFCC5538)))
        DashboardTone.WARNING -> Brush.linearGradient(listOf(Color(0xFF5F3B0A), Color(0xFFD58A1A)))
        DashboardTone.MUTED -> Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
        else -> Brush.linearGradient(listOf(Color(0xFF153B49), Color(0xFF2E6F78)))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .background(brush)
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = status?.currentState?.displayLabel() ?: "offline",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = dashboardModel?.headline ?: "Приложение готово к первичной настройке",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = dashboardModel?.supportingText
                        ?: "Укажите base URL, emergencyId и panicSecret, затем выполните первичную проверку подключения.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Text(
                    text = if (lastSyncMs != null) {
                        "Последняя синхронизация: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSyncMs))}"
                    } else {
                        "Синхронизации еще не было"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
private fun PanicActionCard(
    dashboardModel: DashboardModel?,
    countdownSeconds: Int?,
    isBusy: Boolean,
    onPrimaryAction: () -> Unit,
) {
    val actionEnabled = dashboardModel?.actionEnabled == true && !isBusy
    val buttonColor by animateColorAsState(
        targetValue = when (dashboardModel?.tone) {
            DashboardTone.DANGER -> Color(0xFFC74A2F)
            DashboardTone.WARNING -> Color(0xFFD58A1A)
            DashboardTone.MUTED -> Color(0xFF475569)
            else -> Color(0xFF1D7A85)
        },
        label = "panic-button-color",
    )
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Panic control",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                buttonColor,
                                buttonColor.copy(alpha = 0.84f),
                                MaterialTheme.colorScheme.inverseSurface,
                            ),
                        ),
                    )
                    .clickable(enabled = actionEnabled, onClick = onPrimaryAction),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = dashboardModel?.actionLabel ?: "Откройте настройки",
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = when {
                            isBusy -> "Выполняется запрос..."
                            countdownSeconds != null -> "До автосброса подтверждения: ${countdownSeconds}s"
                            dashboardModel != null -> dashboardModel.supportingText
                            else -> "Сначала сохраните параметры panic-доступа"
                        },
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    baseUrl: String,
    emergencyId: String,
    deviceId: String,
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Подключение и идентификаторы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            MetaRow(label = "Base URL", value = baseUrl)
            HorizontalDivider()
            MetaRow(label = "Emergency ID", value = emergencyId.maskMiddle())
            HorizontalDivider()
            MetaRow(label = "Device ID", value = deviceId.maskMiddle())
            HorizontalDivider()
            Text(
                text = "Если сервер запускается на хосте и в приложении указан локальный адрес хоста, пробросьте тот же порт командой `adb reverse tcp:<PORT> tcp:<PORT>` перед запуском demo в `emulator -avd my_avd`.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun HeroCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF281514), Color(0xFF7A2313), Color(0xFFB93C24)),
                    ),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = body,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SupportingChecklistCard(
    title: String,
    lines: List<String>,
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InlineError(error: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PrimaryPillButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun rememberCountdownSeconds(deadlineMs: Long?): Int? {
    val value by produceState<Int?>(initialValue = deadlineMs.remainingSeconds(), deadlineMs) {
        if (deadlineMs == null) {
            value = null
            return@produceState
        }
        while (true) {
            val remaining = deadlineMs.remainingSeconds()
            value = remaining
            if (remaining == null) {
                break
            }
            delay(250)
        }
    }
    return value
}

private fun Long?.remainingSeconds(): Int? {
    val deadline = this ?: return null
    val deltaMs = deadline - System.currentTimeMillis()
    return if (deltaMs <= 0) null else ceil(deltaMs / 1000.0).toInt()
}
