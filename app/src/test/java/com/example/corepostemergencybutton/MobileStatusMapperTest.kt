package com.example.corepostemergencybutton

import com.example.corepostemergencybutton.data.DashboardTone
import com.example.corepostemergencybutton.data.DeviceState
import com.example.corepostemergencybutton.data.MobileStatus
import com.example.corepostemergencybutton.data.PrimaryActionKind
import com.example.corepostemergencybutton.data.toDashboardModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileStatusMapperTest {
    @Test
    fun pendingLock_requiresConfirmationAction() {
        val status = MobileStatus(
            deviceId = "device-1",
            currentState = DeviceState.PENDING_LOCK,
            userCanUnlock = true,
            needLockApproval = true,
            lockApprovalTimeSecond = 30,
        )

        val model = status.toDashboardModel(isAwaitingSecondTap = true)

        assertEquals(PrimaryActionKind.CONFIRM_LOCK, model.actionKind)
        assertEquals(DashboardTone.WARNING, model.tone)
        assertTrue(model.actionEnabled)
    }

    @Test
    fun lockedWithoutUserUnlock_disablesPrimaryAction() {
        val status = MobileStatus(
            deviceId = "device-1",
            currentState = DeviceState.LOCKED,
            userCanUnlock = false,
            needLockApproval = true,
            lockApprovalTimeSecond = 30,
        )

        val model = status.toDashboardModel(isAwaitingSecondTap = false)

        assertEquals(PrimaryActionKind.NONE, model.actionKind)
        assertEquals(DashboardTone.DANGER, model.tone)
        assertFalse(model.actionEnabled)
    }
}
