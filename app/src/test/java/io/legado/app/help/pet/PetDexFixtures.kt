package io.legado.app.help.pet

/** Actual javac + D8 fixtures; these bytes must never be loaded as executable classes by a test. */
internal object PetDexFixtures {
    fun minimal(): ByteArray = bytes("minimal.dex")
    fun bytes(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/pet/$name")) {
        "Missing DEX test resource: $name"
    }.use { it.readBytes() }
}
