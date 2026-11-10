package io.legado.plugins.pets.blue_fish

import io.legado.pet.api.PetAssets
import io.legado.pet.api.PetPlugin
import io.legado.pet.api.PetSession
import io.legado.plugins.pets.layered.BlueFishPetRenderer
import io.legado.plugins.pets.layered.core.BlueFishPetEngine
import io.legado.plugins.pets.layered.core.LayeredPetSession

class BlueFishPlugin : PetPlugin {
    override val id = "blue_fish"
    override fun createSession(assets: PetAssets): PetSession =
        LayeredPetSession.create(assets, BlueFishPetEngine(), ::BlueFishPetRenderer)
}
