package io.legado.app.help.pet

import io.legado.app.help.plugin.CompiledPackageInspector
import java.util.zip.ZipFile

/** The shared inspector validates bytes without defining or initializing plugin classes. */
internal object PetDexInspector {
    fun validate(archive: ZipFile, manifest: PetPluginManifest) = CompiledPackageInspector.validate(
        archive, manifest.entryClass, "Lio/legado/pet/api/PetPlugin;", "Lio/legado/pet/api/"
    )
}
