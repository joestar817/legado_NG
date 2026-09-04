package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val versionName: String,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String,
    val fileSize: Long,
    val sha256: String,
) {
    internal val version: AppVersion = requireNotNull(AppVersion.parse(versionName))
}

enum class AppVariant {
    OFFICIAL,
    BETA_RELEASE,
    UNKNOWN;

    fun isBeta(): Boolean {
        return this == BETA_RELEASE
    }

    companion object {
        fun fromUpdateChannel(channel: String): AppVariant {
            return when (channel) {
                "official" -> OFFICIAL
                "beta" -> BETA_RELEASE
                else -> UNKNOWN
            }
        }
    }
}

@Keep
data class GithubRelease(
    @SerializedName("assets")
    val assets: List<Asset>?,
    @SerializedName("body")
    val body: String?,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
    @SerializedName("tag_name")
    val tagName: String,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        val appVariant = if (isPreRelease) AppVariant.BETA_RELEASE else AppVariant.OFFICIAL
        val tagVersion = if (appVariant == AppVariant.OFFICIAL) {
            AppVersion.parse(tagName)
        } else {
            null
        }
        val releases = assets
            .filter { it.isValid }
            .mapNotNull { asset ->
                val assetVersion = AppVersion.fromAssetName(asset.name, appVariant)
                    ?: return@mapNotNull null
                if (tagVersion != null && tagVersion != assetVersion) return@mapNotNull null
                asset.assetToAppReleaseInfo(
                    appVariant = appVariant,
                    version = tagVersion ?: assetVersion,
                    note = body.orEmpty(),
                )
            }
        if (releases.isEmpty()) {
            throw NoStackTraceException("没有可用的更新安装包")
        }
        return releases
    }
}

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("download_count")
    val downloadCount: Int,
    @SerializedName("digest")
    val digest: String?,
    @SerializedName("id")
    val id: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("state")
    val state: String,
    @SerializedName("url")
    val url: String,
) {
    private val sha256: String?
        get() = digest
            ?.removePrefix("sha256:")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }

    val isValid: Boolean
        get() = contentType == "application/vnd.android.package-archive" &&
            state == "uploaded" &&
            size > 0L &&
            sha256 != null &&
            AppUpdateRelay.isAllowedReleaseDownloadUrl(apkUrl, name)

    internal fun assetToAppReleaseInfo(
        appVariant: AppVariant,
        version: AppVersion,
        note: String,
    ): AppReleaseInfo {
        val instant = Instant.parse(createdAt)
        val timestamp: Long = instant.toEpochMilli()
        return AppReleaseInfo(
            appVariant = appVariant,
            createdAt = timestamp,
            versionName = version.value,
            note = note,
            name = name,
            downloadUrl = apkUrl,
            assetUrl = url,
            fileSize = size,
            sha256 = requireNotNull(sha256),
        )
    }
}

