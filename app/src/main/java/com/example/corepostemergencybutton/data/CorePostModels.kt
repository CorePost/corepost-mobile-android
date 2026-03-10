package com.example.corepostemergencybutton.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DeviceState(val wireValue: String) {
    REGISTERED("registered"),
    NORMAL("normal"),
    PENDING_LOCK("pending_lock"),
    LOCKED("locked"),
    RESTRICTED("restricted"),
    RECOVERED("recovered");

    companion object {
        fun fromWire(value: String): DeviceState =
            entries.firstOrNull { it.wireValue == value } ?: NORMAL
    }
}

enum class PrimaryActionKind {
    LOCK,
    CONFIRM_LOCK,
    UNLOCK,
    NONE,
}

enum class DashboardTone {
    CALM,
    WARNING,
    DANGER,
    MUTED,
}

@Serializable
data class MobileStatusResponse(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("currentState") val currentState: String,
    @SerialName("userCanUnlock") val userCanUnlock: Boolean,
    @SerialName("needLockApproval") val needLockApproval: Boolean,
    @SerialName("lockApprovalTimeSecond") val lockApprovalTimeSecond: Int,
)

@Serializable
data class MobileActionResponse(
    @SerialName("detail") val detail: String? = null,
    @SerialName("currentState") val currentState: String? = null,
)

data class MobileStatus(
    val deviceId: String,
    val currentState: DeviceState,
    val userCanUnlock: Boolean,
    val needLockApproval: Boolean,
    val lockApprovalTimeSecond: Int,
)

data class MobileActionResult(
    val httpStatus: Int,
    val detail: String,
    val currentState: DeviceState?,
)

data class DashboardModel(
    val headline: String,
    val supportingText: String,
    val actionLabel: String,
    val actionKind: PrimaryActionKind,
    val actionEnabled: Boolean,
    val tone: DashboardTone,
)

fun MobileStatusResponse.toDomain(): MobileStatus =
    MobileStatus(
        deviceId = deviceId,
        currentState = DeviceState.fromWire(currentState),
        userCanUnlock = userCanUnlock,
        needLockApproval = needLockApproval,
        lockApprovalTimeSecond = lockApprovalTimeSecond,
    )

fun MobileStatus.toDashboardModel(isAwaitingSecondTap: Boolean): DashboardModel {
    return when (currentState) {
        DeviceState.REGISTERED -> DashboardModel(
            headline = "Устройство зарегистрировано",
            supportingText = "Panic-lock уже доступен. Сервер еще не был переведен в штатный режим, но аварийная блокировка сработает.",
            actionLabel = if (isAwaitingSecondTap) "Подтвердить блокировку" else "Включить panic-lock",
            actionKind = if (isAwaitingSecondTap) PrimaryActionKind.CONFIRM_LOCK else PrimaryActionKind.LOCK,
            actionEnabled = true,
            tone = DashboardTone.CALM,
        )

        DeviceState.NORMAL -> DashboardModel(
            headline = "Штатный рабочий режим",
            supportingText = "Ноутбук считается доверенным. Один тап инициирует аварийную блокировку, второй подтверждает ее при `pending_lock`.",
            actionLabel = if (isAwaitingSecondTap) "Подтвердить блокировку" else "Включить panic-lock",
            actionKind = if (isAwaitingSecondTap) PrimaryActionKind.CONFIRM_LOCK else PrimaryActionKind.LOCK,
            actionEnabled = true,
            tone = DashboardTone.CALM,
        )

        DeviceState.PENDING_LOCK -> DashboardModel(
            headline = "Ожидается подтверждение",
            supportingText = "Сервер уже открыл окно подтверждения. Повторный тап переводит устройство в `locked`.",
            actionLabel = "Подтвердить блокировку",
            actionKind = PrimaryActionKind.CONFIRM_LOCK,
            actionEnabled = true,
            tone = DashboardTone.WARNING,
        )

        DeviceState.LOCKED -> DashboardModel(
            headline = "Устройство заблокировано",
            supportingText = if (userCanUnlock) {
                "Предзагрузочная расшифровка будет отклоняться сервером. Вы можете вернуть устройство в состояние `recovered`."
            } else {
                "Пользовательская разблокировка запрещена серверной политикой. Нужен администратор."
            },
            actionLabel = if (userCanUnlock) "Снять блокировку" else "Ожидается администратор",
            actionKind = if (userCanUnlock) PrimaryActionKind.UNLOCK else PrimaryActionKind.NONE,
            actionEnabled = userCanUnlock,
            tone = DashboardTone.DANGER,
        )

        DeviceState.RESTRICTED -> DashboardModel(
            headline = "Ограниченный режим",
            supportingText = "Состояние `restricted` требует административного решения. Panic-клиент здесь только отображает статус.",
            actionLabel = "Доступ ограничен",
            actionKind = PrimaryActionKind.NONE,
            actionEnabled = false,
            tone = DashboardTone.MUTED,
        )

        DeviceState.RECOVERED -> DashboardModel(
            headline = "Доступ восстановлен",
            supportingText = "Устройство переведено в `recovered`. При необходимости можно снова активировать panic-lock.",
            actionLabel = if (isAwaitingSecondTap) "Подтвердить блокировку" else "Включить panic-lock",
            actionKind = if (isAwaitingSecondTap) PrimaryActionKind.CONFIRM_LOCK else PrimaryActionKind.LOCK,
            actionEnabled = true,
            tone = DashboardTone.CALM,
        )
    }
}

fun DeviceState.displayLabel(): String = when (this) {
    DeviceState.REGISTERED -> "registered"
    DeviceState.NORMAL -> "normal"
    DeviceState.PENDING_LOCK -> "pending_lock"
    DeviceState.LOCKED -> "locked"
    DeviceState.RESTRICTED -> "restricted"
    DeviceState.RECOVERED -> "recovered"
}
