package io.legado.app.integration.video

import android.annotation.SuppressLint
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import io.legado.features.preload.StandbyBufferBudget

/** Forward Media3's Java default methods explicitly; Kotlin interface delegation omits them. */
@SuppressLint("UnsafeOptInUsageError")
internal class NgVideoLoadControl(
    private val delegate: LoadControl,
    private val budget: StandbyBufferBudget,
) : LoadControl {
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray,
                                  trackSelections: Array<out ExoTrackSelection?>) =
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)
    override fun getAllocator(playerId: PlayerId) = delegate.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId) = delegate.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        budget.canLoad(parameters.bufferedDurationUs,
            delegate.getAllocator(parameters.playerId).totalBytesAllocated) && delegate.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters) = delegate.shouldStartPlayback(parameters)

    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline,
                                         mediaPeriodId: MediaPeriodId, bufferedDurationUs: Long) =
        delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
}
