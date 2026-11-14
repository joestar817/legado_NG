package io.legado.app.help.config

import io.legado.app.help.resource.ResourceProtocol
import io.legado.app.help.resource.ResourceInstallMode
import io.legado.app.utils.GSON

/** A snapshot of the exact preset the user is being asked to replace. */
internal data class ReadPresetResourceCandidate(
    val index: Int,
    val name: String,
    val resourceTarget: String?,
    val fingerprint: String,
    val selected: Boolean,
)

internal data class ReadPresetResourceConflict(
    val payloadSha256: String,
    val previousTarget: String?,
    val isUpdate: Boolean,
    val candidates: List<ReadPresetResourceCandidate>,
)

internal fun readPresetConfirmationValid(
    current: ReadPresetResourceConflict?,
    confirmed: ReadPresetResourceConflict?,
    mode: ResourceInstallMode,
    replacement: ReadPresetResourceCandidate?,
): Boolean = if (current == null) confirmed == null else
    mode != ResourceInstallMode.NORMAL && confirmed == current &&
        (mode != ResourceInstallMode.REPLACE || replacement in current.candidates)

internal fun readPresetResourceConflict(
    configs: List<ReadBookConfig.Config>,
    name: String,
    previousTarget: String?,
    sha256: String,
    selectedIndices: Set<Int>,
): ReadPresetResourceConflict? {
    val owned = previousTarget?.let { target -> configs.indices.filter { configs[it].ngResourcePresetTarget == target } }.orEmpty()
    val indices = owned.ifEmpty { configs.indices.filter { configs[it].name == name } }
    if (indices.isEmpty()) return null
    return ReadPresetResourceConflict(sha256, previousTarget, owned.isNotEmpty(), indices.map { index ->
        val config = configs[index]
        ReadPresetResourceCandidate(
            index = index,
            name = config.name,
            resourceTarget = config.ngResourcePresetTarget,
            fingerprint = ResourceProtocol.hash(GSON.toJson(config).toByteArray(Charsets.UTF_8)),
            selected = index in selectedIndices,
        )
    })
}

/** Add a new preset without changing any existing preset or selected position. */
internal fun appendReadPresetResource(
    configs: MutableList<ReadBookConfig.Config>,
    config: ReadBookConfig.Config,
    target: String,
    sha256: String,
    persist: (List<ReadBookConfig.Config>) -> Unit,
) {
    require(target.isNotBlank() && sha256.matches(Regex("[a-f0-9]{64}"))) { "预设安装身份不合法" }
    require(configs.none { it.ngResourcePresetTarget == target }) { "预设安装身份重复" }
    val baseName = config.name.ifBlank { "阅读预设" }
    val names = configs.mapTo(hashSetOf()) { it.name }
    config.name = if (baseName !in names) baseName else generateSequence(2) { it + 1 }
        .map { "$baseName ($it)" }.first { it !in names }
    config.ngResourcePresetTarget = target
    config.ngResourcePresetSha256 = sha256
    configs.add(config)
    try {
        persist(configs)
    } catch (error: Throwable) {
        configs.removeAt(configs.lastIndex)
        throw error
    }
}

/** Replace only the confirmed position, and restore the original if persistence fails. */
internal fun replaceReadPresetResource(
    configs: MutableList<ReadBookConfig.Config>,
    config: ReadBookConfig.Config,
    candidate: ReadPresetResourceCandidate,
    target: String,
    sha256: String,
    persist: (List<ReadBookConfig.Config>) -> Unit,
) {
    require(target.isNotBlank() && sha256.matches(Regex("[a-f0-9]{64}"))) { "预设安装身份不合法" }
    val previous = configs.getOrNull(candidate.index) ?: error("预设已发生变化，请重新选择")
    require(ResourceProtocol.hash(GSON.toJson(previous).toByteArray(Charsets.UTF_8)) == candidate.fingerprint) {
        "预设已发生变化，请重新选择"
    }
    require(configs.withIndex().none { it.index != candidate.index && it.value.ngResourcePresetTarget == target }) {
        "预设安装身份重复"
    }
    config.name = previous.name
    config.ngResourcePresetTarget = target
    config.ngResourcePresetSha256 = sha256
    configs[candidate.index] = config
    try {
        persist(configs)
    } catch (error: Throwable) {
        configs[candidate.index] = previous
        throw error
    }
}

internal fun readPresetResourceHash(configs: List<ReadBookConfig.Config>, target: String): String? =
    configs.singleOrNull { it.ngResourcePresetTarget == target }
        ?.ngResourcePresetSha256?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
