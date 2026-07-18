package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSkillPinnedTrustTest {

    @Test
    fun pinnedSkillIsTrustedOnlyWhenItMatchesCurrentBuiltInIdentity() {
        assertTrue(
            isPinnedSkillTrustedBuiltIn(
                currentTrusted = true,
                currentContentHash = "hash-current",
                currentRuntimeRevision = "skill@31@hash-current",
                pinnedContentHash = "hash-current",
                pinnedRuntimeRevision = "skill@31@hash-current"
            )
        )
        assertFalse(
            isPinnedSkillTrustedBuiltIn(
                currentTrusted = true,
                currentContentHash = "hash-current",
                currentRuntimeRevision = "skill@31@hash-current",
                pinnedContentHash = "hash-edited",
                pinnedRuntimeRevision = "skill@30@hash-edited"
            )
        )
        assertFalse(
            isPinnedSkillTrustedBuiltIn(
                currentTrusted = false,
                currentContentHash = "hash-current",
                currentRuntimeRevision = "skill@31@hash-current",
                pinnedContentHash = "hash-current",
                pinnedRuntimeRevision = "skill@31@hash-current"
            )
        )
    }
}
