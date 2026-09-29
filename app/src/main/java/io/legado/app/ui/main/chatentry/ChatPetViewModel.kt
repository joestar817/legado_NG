package io.legado.app.ui.main.chatentry

import android.os.Bundle
import androidx.lifecycle.ViewModel
import io.legado.app.ui.main.chatentry.core.ChatPetEngine
import io.legado.app.ui.main.chatentry.core.GuguGagaCharacter
import io.legado.app.ui.main.chatentry.core.PetPlacement
import io.legado.app.ui.main.chatentry.core.PetPose

/** Retains animation state across rotation without retaining an Activity or bitmap. */
class ChatPetViewModel : ViewModel() {
    private val characters = listOf(GuguGagaCharacter.definition).associateBy { it.id }
    private val engines = mutableMapOf<String, ChatPetEngine>()

    fun engineFor(characterId: String): ChatPetEngine? {
        val definition = characters[characterId] ?: return null
        return engines.getOrPut(characterId) { ChatPetEngine(definition) }
    }

    fun restorePlacements(saved: Bundle?) {
        if (saved == null || engines.isNotEmpty()) return
        for (id in saved.keySet()) {
            val placement = saved.getBundle(id) ?: continue
            val x = placement.getFloat("x", 1f)
            val y = placement.getFloat("y", .69f)
            if (!x.isFinite() || !y.isFinite()) continue
            val pose = when (placement.getString("pose")) {
                "peek" -> PetPose.PEEK
                "full" -> PetPose.FULL
                else -> continue
            }
            engineFor(id)?.restorePlacement(PetPlacement(pose, x, y))
        }
    }

    fun savePlacements() = Bundle().apply {
        for ((id, engine) in engines) {
            val placement = engine.placement()
            putBundle(id, Bundle().apply {
                putString("pose", if (placement.pose == PetPose.PEEK) "peek" else "full")
                putFloat("x", placement.xFraction)
                putFloat("y", placement.yFraction)
            })
        }
    }
}
