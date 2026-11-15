package io.legado.features.preload

class StandbyBufferBudget(
    private val durationUs: Long = 8_000_000,
    private val bytes: Int = 4 * 1024 * 1024,
) {
    @Volatile var standby = true
        private set

    fun activate() { standby = false }
    fun canLoad(bufferedDurationUs: Long, allocatedBytes: Int): Boolean =
        !standby || bufferedDurationUs < durationUs && allocatedBytes < bytes
}
