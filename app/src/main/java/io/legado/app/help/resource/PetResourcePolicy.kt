package io.legado.app.help.resource

import io.legado.app.help.pet.PetLocalRecord
import io.legado.app.help.pet.PetLocalStatus

/** Compare the actual local installation, never a stale online receipt's version. */
internal object PetResourcePolicy {
    fun present(pet: PetLocalRecord?) = pet != null && pet.status != PetLocalStatus.MISSING

    fun identical(entry: ResourceEntry, pet: PetLocalRecord?) = pet != null &&
        pet.id == entry.item.payload.pluginId && pet.sha256 == entry.item.payload.sha256 &&
        pet.manifest?.versionCode == entry.item.versionCode

    fun matchingReceipt(pet: PetLocalRecord, receipts: List<ResourceInstallation>): ResourceInstallation? =
        receipts.firstOrNull {
            it.repository == ResourceProtocol.REPOSITORY && it.entry.category.type == "pet" &&
                it.target == pet.id && it.entry.item.payload.pluginId == pet.id &&
                it.baseline == pet.sha256 && it.entry.item.payload.sha256 == pet.sha256
        }

    fun replacementAllowed(entry: ResourceEntry, pet: PetLocalRecord?, mode: ResourceInstallMode) =
        mode == ResourceInstallMode.REPLACE || (identical(entry, pet) &&
            pet?.status in listOf(PetLocalStatus.MISSING, PetLocalStatus.CORRUPT))

    fun owned(entry: ResourceEntry, pet: PetLocalRecord?, receipt: ResourceInstallation?): Boolean {
        pet ?: return false
        val origin = pet.origin
        if (origin != null) return origin.channel == "resource" &&
            origin.repository == ResourceProtocol.REPOSITORY && origin.resourceId == entry.item.id &&
            origin.sha256 == pet.sha256
        return receipt?.entry?.category?.type == "pet" && receipt.target == pet.id &&
            receipt.repository == ResourceProtocol.REPOSITORY && receipt.baseline == pet.sha256 &&
            receipt.entry.item.payload.sha256 == pet.sha256
    }

    fun localChange(entry: ResourceEntry, pet: PetLocalRecord?, receipt: ResourceInstallation?) =
        present(pet) && !identical(entry, pet) && (!owned(entry, pet, receipt) ||
            pet?.manifest?.versionCode == entry.item.versionCode)

    fun autoUpdate(entry: ResourceEntry, pet: PetLocalRecord?, receipt: ResourceInstallation?) =
        present(pet) && pet?.status != PetLocalStatus.CORRUPT && pet?.manifest != null && owned(entry, pet, receipt) &&
            entry.item.versionCode > pet.manifest.versionCode

    fun label(entry: ResourceEntry, pet: PetLocalRecord?, receipt: ResourceInstallation?): String {
        pet ?: return "未下载"
        if (pet.status == PetLocalStatus.MISSING) return "文件缺失"
        if (pet.status == PetLocalStatus.CORRUPT) return "文件损坏"
        val version = pet.manifest?.versionCode ?: return "文件损坏"
        if (version > entry.item.versionCode) return "本地版本较新"
        if (identical(entry, pet)) return if (pet.status == PetLocalStatus.LOAD_ERROR) "运行失败" else "已下载"
        return if (localChange(entry, pet, receipt)) "本地修改版" else "可更新"
    }
}
