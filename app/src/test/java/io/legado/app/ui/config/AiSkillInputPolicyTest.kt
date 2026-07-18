package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSkillInputPolicyTest {

    @Test
    fun systemModeShowsFixedSkillWithoutSelectorOrRemoval() {
        val policy = resolveSkillInputPolicy(
            hasActiveSkill = true,
            allowsUserSkills = false,
            modelSupportsToolCalls = true
        )

        assertFalse(policy.selectorVisible)
        assertFalse(policy.removable)
        assertFalse(policy.toolError)
    }

    @Test
    fun activeSkillReportsUnsupportedToolModel() {
        val policy = resolveSkillInputPolicy(
            hasActiveSkill = true,
            allowsUserSkills = false,
            modelSupportsToolCalls = false
        )

        assertTrue(policy.toolError)
    }

    @Test
    fun generalModeKeepsSkillSelectorAndRemoval() {
        val policy = resolveSkillInputPolicy(
            hasActiveSkill = true,
            allowsUserSkills = true,
            modelSupportsToolCalls = true
        )

        assertTrue(policy.selectorVisible)
        assertTrue(policy.removable)
        assertFalse(policy.toolError)
    }

    @Test
    fun generalModeLocksSkillPreloadedBySpecialEntry() {
        val policy = resolveSkillInputPolicy(
            hasActiveSkill = true,
            allowsUserSkills = true,
            fixedEntrySkill = true,
            modelSupportsToolCalls = true
        )

        assertFalse(policy.selectorVisible)
        assertFalse(policy.removable)
        assertFalse(policy.toolError)
    }

    @Test
    fun ordinaryChatDoesNotReportToolErrorWithoutSkill() {
        val policy = resolveSkillInputPolicy(
            hasActiveSkill = false,
            allowsUserSkills = true,
            modelSupportsToolCalls = false
        )

        assertFalse(policy.toolError)
    }
}
