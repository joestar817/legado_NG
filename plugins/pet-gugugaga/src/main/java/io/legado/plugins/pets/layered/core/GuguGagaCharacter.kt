package io.legado.plugins.pets.layered.core

object GuguGagaCharacter {
    val definition = PetCharacterDefinition(
        id = "gugugaga",
        assetDirectory = "ai_pets/gugugaga",
        scale = 0.23f,
        fullLayers = listOf(
            PetLayerDefinition("tail", "front/tail.png", 334f, 510f, 104f, 61f, 5, 13f, 30f),
            PetLayerDefinition("left-foot", "front/left-foot.png", 197f, 552f, 52f, 42f, 10, 26f, 39f),
            PetLayerDefinition("right-foot", "front/right-foot.png", 263f, 552f, 52f, 42f, 10, 26f, 39f),
            PetLayerDefinition("left-arm", "front/left-arm.png", 103.6f, 378f, 108f, 161f, 20, 86.4f, 18f),
            PetLayerDefinition("right-arm", "front/right-arm.png", 300.4f, 378f, 108f, 157f, 20, 21.6f, 18f),
            PetLayerDefinition("torso", "front/torso.png", 144f, 355f, 224f, 215f, 30, 112f, 30f),
            PetLayerDefinition("head", "front/head.png", 106f, 110f, 300f, 270f, 40, 150f, 251f),
            PetLayerDefinition("eyes-closed", "front/eyes-closed.png", 106f, 110f, 300f, 270f, 41, parentId = "head"),
            PetLayerDefinition("mouth-shout", "front/mouth-shout.png", 106f, 110f, 300f, 270f, 42, parentId = "head"),
        ),
        peekLayers = listOf(
            PetLayerDefinition("body", "peek/body.png", 192f, 348f, 190f, 250f, 30, 94f, 31f),
            PetLayerDefinition("head", "peek/head.png", 94f, 110f, 324f, 289.4f, 40, 188f, 270f),
            PetLayerDefinition("eyes-closed", "peek/eyes-closed.png", 94f, 110f, 324f, 289.4f, 41, parentId = "head"),
            PetLayerDefinition("mouth-neutral", "peek/mouth-neutral.png", 94f, 110f, 324f, 289.4f, 42, parentId = "head"),
        ),
    )
}
