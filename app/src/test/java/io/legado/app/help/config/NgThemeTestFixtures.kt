package io.legado.app.help.config

import io.legado.app.utils.GSON
import java.io.File

/** Content lives outside the APK; shared test samples read the real portable manifests. */
internal object NgThemeTestFixtures {
    val contentRoot: File get() = sequenceOf(File("themes/content"), File("../themes/content"))
        .first { it.isDirectory }

    fun manifest(slug: String): NgThemePackageManifest = GSON.fromJson(
        File(contentRoot, "$slug/manifest.json").readText(), NgThemePackageManifest::class.java,
    )

    val autumn get() = manifest("autumn-mountains").theme
    val storybook get() = manifest("storybook-house").theme
}
