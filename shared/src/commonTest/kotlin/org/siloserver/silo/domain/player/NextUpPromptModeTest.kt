package org.siloserver.silo.domain.player

import kotlin.test.Test
import kotlin.test.assertEquals

class NextUpPromptModeTest {
    @Test fun originalCreditsChoiceRemainsEnabled() {
        assertEquals(NextUpPromptMode.Enabled, NextUpPromptMode.fromWire("credits"))
        assertEquals(NextUpPromptMode.Enabled, NextUpPromptMode.fromWire(null))
    }

    @Test fun optingOutOfCreditsTriggersRemainsDisabledAfterUpgrade() {
        assertEquals(NextUpPromptMode.Disabled, NextUpPromptMode.fromWire("off"))
        assertEquals(NextUpPromptMode.Disabled, NextUpPromptMode.fromWire("time_remaining"))
    }
}
