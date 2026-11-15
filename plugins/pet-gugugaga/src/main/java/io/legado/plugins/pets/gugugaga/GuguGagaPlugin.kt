package io.legado.plugins.pets.gugugaga

import io.legado.pet.api.PetAssets
import io.legado.pet.api.PetPlugin
import io.legado.pet.api.PetSession
import io.legado.plugins.pets.layered.GuguGagaPetRenderer
import io.legado.plugins.pets.layered.core.ChatPetEngine
import io.legado.plugins.pets.layered.core.LayeredPetSession

class GuguGagaPlugin : PetPlugin {
    override val id = "gugugaga"
    override fun createSession(assets: PetAssets): PetSession =
        LayeredPetSession.create(assets, ChatPetEngine(), ::GuguGagaPetRenderer)
}
