package io.legado.app.ui.main.chatentry.core

/** Registered source canvas geometry from the accepted blue-fish v3 design. */
object BlueFishCharacter {
    const val HEIGHT_DP = 128f
    const val SOURCE_SIZE = 1254f
    const val ROOT_X = 640f
    const val ROOT_Y = 1230f

    val definition = PetCharacterDefinition(
        id = "blue_fish",
        assetDirectory = "ai_pets/blue_fish",
        scale = HEIGHT_DP / 1200f,
        fullLayers = listOf(
            PetLayerDefinition("head", "standing-body.png", 0f, 0f, SOURCE_SIZE, SOURCE_SIZE, 0, ROOT_X, ROOT_Y),
        ),
        peekLayers = listOf(
            PetLayerDefinition("head", "peek.png", 0f, 0f, SOURCE_SIZE, SOURCE_SIZE, 0, ROOT_X, ROOT_Y),
        ),
        peekRestCutX = 480f,
        peekMoreCutX = 630f,
        additionalAssets = listOf(
            "standing-tail.png", "eat-body.png", "eat-arm.png", "eat-tail.png",
            "mouth-open.png", "rice-bite.png", "peek-blink.png", "idle-blink.png",
            "think.png", "aha.png", "rice-grains.png", "drag-surprised.png", "release-shy.png",
        ),
        bitmapSampleSize = 2,
    )
}
