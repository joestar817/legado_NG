package io.legado.app.help.update

internal data class AppVersion(
    val value: String,
    private val major: Int,
    private val year: Int,
    private val buildTime: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        return compareValuesBy(this, other, AppVersion::major, AppVersion::year, AppVersion::buildTime)
    }

    companion object {
        private val versionRegex = Regex("^(\\d+)\\.(\\d{2})\\.(\\d{6})(?:-?debug)?$")
        private val assetNameRegex =
            Regex("^legado_NG_(\\d+\\.\\d{2}\\.\\d{6})\\d{2}_(release|beta)\\.apk$")

        fun parse(value: String): AppVersion? {
            val match = versionRegex.matchEntire(value.trim()) ?: return null
            return AppVersion(
                value = match.groupValues.take(4).drop(1).joinToString("."),
                major = match.groupValues[1].toInt(),
                year = match.groupValues[2].toInt(),
                buildTime = match.groupValues[3].toInt(),
            )
        }

        fun fromAssetName(name: String, variant: AppVariant): AppVersion? {
            val match = assetNameRegex.matchEntire(name) ?: return null
            val expectedSuffix = when (variant) {
                AppVariant.OFFICIAL -> "release"
                AppVariant.BETA_RELEASE -> "beta"
                AppVariant.UNKNOWN -> return null
            }
            if (match.groupValues[2] != expectedSuffix) return null
            return parse(match.groupValues[1])
        }
    }
}
