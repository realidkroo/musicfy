package com.example.musicfy.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicfy.crossmix.CrossmixAnalysisRepository
import com.example.musicfy.crossmix.CrossmixPlan
import com.example.musicfy.crossmix.CrossmixPlanner
import com.example.musicfy.crossmix.CrossmixRole
import com.example.musicfy.crossmix.CrossmixStyle
import com.example.musicfy.crossmix.CrossmixTrackInput
import com.example.musicfy.playback.audio.CrossmixTransitionAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Runs Crossmix transitions: analyses the song and the next one, plans the hand-over, then plays
 * it on two players.
 *
 * The rule throughout is that a transition either happens properly or not at all. The next song
 * is prepared silently well ahead; only once it's actually ready is the outgoing side touched, and
 * if it isn't ready in time the song simply ends and the next one starts as it always did. The
 * next player starts slightly early by a measured amount and its beat is then locked to the
 * outgoing one by a small delay inside its renderer, mostly while it's still silent. Tempo is set
 * once before it starts and eased back after the overlap, never moved while both are playing,
 * because Media3 drains and resets the whole audio chain on every speed change.
 */
@UnstableApi
class CrossmixDirector(
    private val scope: CoroutineScope,
    private val host: Host,
    private val analyses: CrossmixAnalysisRepository,
) {
    interface Host {
        val currentPlayer: ExoPlayer
        fun processorFor(player: Player): CrossmixTransitionAudioProcessor?
        /** a new player holding the queue, at [index] / [positionMs], paused, at the matched speed */
        fun createIncoming(index: Int, positionMs: Long, speedRatio: Float): ExoPlayer
        /** [incoming] becomes the session's player; [outgoing] plays on as the fading side */
        fun promote(incoming: ExoPlayer, outgoing: ExoPlayer)
        /** the outgoing side has finished: let its player go */
        fun release(outgoing: ExoPlayer)
        /** a prepared next player that won't be used after all */
        fun discard(incoming: ExoPlayer)
        /** Crossmix is on and nothing (casting, a song-end sleep timer, a gapless album) rules it out */
        fun allowsCrossmix(): Boolean
        fun nextIndex(): Int
        suspend fun lyrics(mediaId: String): String?
        /** the listener's own speed and pitch, which the transition returns to */
        fun baseParameters(): PlaybackParameters
    }

    private var planningJob: Job? = null
    private var planningKey: String? = null
    private var armed: ExoPlayer? = null
    private var running: Running? = null
    private val recentStyles = ArrayDeque<CrossmixStyle>()

    // how long play() takes to be heard, learnt from each transition's first beat-lock reading
    private var startLatencyMs = 70.0

    val isTransitioning: Boolean get() = running != null

    private class Running(
        val plan: CrossmixPlan,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outProcessor: CrossmixTransitionAudioProcessor,
        val inProcessor: CrossmixTransitionAudioProcessor,
        val base: PlaybackParameters,
    ) {
        val jobs = ArrayList<Job>()
        var released = false
        var tempoSettled = false
        var lastSetSpeed = 0f
    }

    /** called whenever the playing song, its position or its state may have changed */
    fun schedule() {
        if (running != null) return
        val player = host.currentPlayer
        val item = player.currentMediaItem ?: return cancelPending()
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < MinTrackMs || !host.allowsCrossmix()) return cancelPending()
        val nextIndex = host.nextIndex()
        if (nextIndex == C.INDEX_UNSET) return cancelPending()
        val next = player.getMediaItemAt(nextIndex)
        val key = "${item.mediaId}→${next.mediaId}@$nextIndex"
        if (key == planningKey && (planningJob?.isActive == true || armed != null)) return
        cancelPending()
        planningKey = key
        planningJob = scope.launch { prepareTransition(player, item, next, nextIndex, duration) }
    }

    /** the listener moved the playhead: a planned transition is re-timed, a running one ends cleanly */
    fun onUserSeek() {
        if (running != null) abortRunning("seek") else { cancelPending(); schedule() }
    }

    /** the song changed under us (a skip, a queue edit) */
    fun onItemChanged() {
        if (running != null) abortRunning("item changed") else cancelPending()
    }

    fun release() {
        cancelPending()
        running?.let { abortRunning("service released") }
    }

    private fun cancelPending() {
        planningJob?.cancel()
        planningJob = null
        planningKey = null
        armed?.let { incoming ->
            host.processorFor(incoming)?.clear()
            host.discard(incoming)
        }
        armed = null
        // the outgoing side is only ever armed right before the hand-over starts; if a transition
        // was called off after that, the song must play on untouched
        host.processorFor(host.currentPlayer)?.let { if (running == null) it.clear() }
    }

    private suspend fun prepareTransition(
        outgoing: ExoPlayer,
        item: MediaItem,
        next: MediaItem,
        nextIndex: Int,
        durationMs: Long,
    ) {
        val outAnalysis = analyses.analyze(item, wholeTrack = true)
        val inAnalysis = analyses.analyze(next, wholeTrack = false)
        val plan = CrossmixPlanner.plan(
            outgoing = CrossmixTrackInput(item.mediaId, durationMs, outAnalysis, host.lyrics(item.mediaId)),
            incoming = CrossmixTrackInput(next.mediaId, 0L, inAnalysis, host.lyrics(next.mediaId)),
            recentStyles = recentStyles.toList(),
        )
        Timber.tag(Tag).d(
            "plan %s %s→%s: %d beats from %d ms, cue %d ms, speed %.4f, matched=%s (bpm %s / %s)",
            plan.style.label, item.mediaId, next.mediaId, plan.lengthBeats, plan.outStartMs, plan.inCueMs,
            plan.inSpeed, plan.beatMatched, outAnalysis?.bpm, inAnalysis?.bpm,
        )

        // the earliest moment either side does anything: the outgoing effects start at beat 0, the
        // next player may start a little before that on a silent run-up
        val firstMoveMs = plan.outMediaAt(min(plan.inStartBeat, 0f))
        if (!waitForOutgoing(outgoing, item, firstMoveMs - ArmLeadMs)) return
        if (!host.allowsCrossmix() || outgoing.currentPosition > firstMoveMs - MinArmLeadMs) return

        val incoming = runCatching { host.createIncoming(nextIndex, plan.inStartMs, plan.inSpeed) }.getOrNull() ?: return
        armed = incoming
        var failed = false
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        incoming.addListener(errorListener)

        // go or no-go, decided before the outgoing side is touched
        val goDeadline = firstMoveMs - GoMarginMs
        while (incoming.playbackState != Player.STATE_READY) {
            if (failed || !stillCurrent(outgoing, item) || outgoing.currentPosition > goDeadline) {
                Timber.tag(Tag).w("next song not ready in time (failed=%s); playing through normally", failed)
                incoming.removeListener(errorListener)
                armed = null
                host.processorFor(incoming)?.clear()
                host.discard(incoming)
                return
            }
            delay(40)
        }
        incoming.removeListener(errorListener)

        val outProcessor = host.processorFor(outgoing) ?: return giveUp(incoming)
        val inProcessor = host.processorFor(incoming) ?: return giveUp(incoming)
        // both sides must be able to render the transition, or neither is touched
        if (!outProcessor.canRender || !inProcessor.canRender) {
            Timber.tag(Tag).w("a side can't render a transition (format); playing through normally")
            return giveUp(incoming)
        }
        val base = host.baseParameters()

        // the incoming side starts on a delay with room to move both ways, plus room for any drift
        // the delay will have to absorb when the tempos are a hair apart but not stretched
        val overlapWallMs = (plan.lengthBeats + 8) * plan.outBeatMs / base.speed
        val inRate = plan.inMediaPerBeat / plan.outBeatMs
        val drift = (inRate - plan.inSpeed) * overlapWallMs
        val initialDelayMs = (BaseDelayMs + max(0.0, drift)).coerceAtMost(400.0)

        outProcessor.arm(script(plan, CrossmixRole.Outgoing, outgoing.currentPosition.toDouble(), System.nanoTime()))
        inProcessor.arm(script(plan, CrossmixRole.Incoming, plan.inStartMs.toDouble(), 0L))
        inProcessor.setBeatLockDelay(initialDelayMs)

        // start the next player early by its start latency and by the delay it begins with
        val playAt = plan.outMediaAt(plan.inStartBeat) - initialDelayMs / plan.inSpeed - startLatencyMs * base.speed
        if (!waitForOutgoing(outgoing, item, playAt, precise = true)) {
            // the song changed in the last moment: put everything back
            outProcessor.clear()
            return giveUp(incoming)
        }

        armed = null
        val session = Running(plan, outgoing, incoming, outProcessor, inProcessor, base)
        running = session
        planningJob = null
        planningKey = null
        recentStyles.addLast(plan.style)
        while (recentStyles.size > 4) recentStyles.removeFirst()

        host.promote(incoming, outgoing)
        incoming.play()
        session.lastSetSpeed = incoming.playbackParameters.speed

        session.jobs += scope.launch { lockBeat(session) }
        session.jobs += scope.launch { releaseOutgoingWhenDone(session) }
    }

    private fun giveUp(incoming: ExoPlayer) {
        armed = null
        host.processorFor(incoming)?.clear()
        host.discard(incoming)
    }

    /**
     * keeps the next song's beat on the outgoing one for as long as they overlap: reads both
     * positions a few times a second and moves the incoming side's delay by the median error.
     * the first readings come while the next song is still silent, so the lock is in before it's heard.
     */
    private suspend fun lockBeat(session: Running) {
        val plan = session.plan
        val errors = ArrayList<Double>()
        var firstCorrection = true
        delay(350)
        while (scope.isActive && running === session) {
            val outgoing = session.outgoing
            val incoming = session.incoming
            if (session.released) break
            if (outgoing.isPlaying && incoming.isPlaying) {
                val outPos = outgoing.currentPosition
                val inPos = incoming.currentPosition
                val beat = ((outPos - plan.outStartMs) / plan.outBeatMs).toFloat()
                if (beat > plan.lengthBeats + 2f) break
                val expected = plan.inMediaAt(beat)
                val heard = inPos - session.inProcessor.currentDelayMs
                errors += heard - expected
                if (errors.size >= 5) {
                    val median = errors.sorted()[errors.size / 2]
                    errors.clear()
                    if (abs(median) < 600.0) {
                        if (firstCorrection) {
                            // a positive error means it was heard early: next time, start it later
                            startLatencyMs = (startLatencyMs - median * 0.5).coerceIn(20.0, 250.0)
                            firstCorrection = false
                        }
                        if (abs(median) > 2.0) {
                            session.inProcessor.setBeatLockDelay(session.inProcessor.currentDelayMs + median)
                        }
                    }
                }
            } else {
                errors.clear()
            }
            delay(LockIntervalMs)
        }
        // the overlap is over: ease the delay away (slowly enough to be inaudible), then the tempo
        session.inProcessor.setBeatLockDelay(0.0)
        relaxTempo(session)
    }

    /** back to the listener's own tempo in steps too small to hear, once the renderer is idle */
    private suspend fun relaxTempo(session: Running) {
        val incoming = session.incoming
        val base = session.base
        // the renderer goes idle once its delay has eased back to nothing; it can't take longer
        // than this, but if it somehow does, step it out rather than hold up every later transition
        val deadline = System.currentTimeMillis() + 90_000L
        while (scope.isActive && !session.inProcessor.idle) {
            if (incoming.currentMediaItemIndex == C.INDEX_UNSET) return
            if (System.currentTimeMillis() > deadline) {
                session.inProcessor.finish()
                delay(1_000)
                break
            }
            delay(500)
        }
        while (scope.isActive) {
            val current = incoming.playbackParameters
            // the listener changed the speed themselves: theirs wins
            if (abs(current.speed - session.lastSetSpeed) > 0.0005f) break
            val gap = base.speed - current.speed
            if (abs(gap) < 0.0005f) {
                if (current.speed != base.speed || current.pitch != base.pitch) incoming.playbackParameters = base
                break
            }
            val step = gap.coerceIn(-SpeedStep, SpeedStep)
            val nextSpeed = current.speed + step
            incoming.playbackParameters = PlaybackParameters(nextSpeed, base.pitch)
            session.lastSetSpeed = nextSpeed
            // two bars between steps
            delay((8 * session.plan.inMediaPerBeat / max(0.25f, nextSpeed)).toLong().coerceIn(1_000L, 8_000L))
        }
        session.tempoSettled = true
        finishSession(session)
    }

    private suspend fun releaseOutgoingWhenDone(session: Running) {
        val plan = session.plan
        val outgoing = session.outgoing
        while (scope.isActive && running === session && !session.released) {
            val ended = outgoing.playbackState == Player.STATE_ENDED ||
                outgoing.playbackState == Player.STATE_IDLE ||
                (!outgoing.playWhenReady && outgoing.currentPosition >= plan.outStartMs + plan.lengthBeats * plan.outBeatMs)
            if (ended || outgoing.currentPosition >= plan.outReleaseMs) break
            delay(200)
        }
        if (session.released) return
        session.outProcessor.fadeOutNow()
        delay(90)
        session.released = true
        host.release(outgoing)
        finishSession(session)
    }

    /** a session is over once the outgoing player is gone and the tempo is the listener's again */
    private fun finishSession(session: Running) {
        if (running !== session) return
        if (!session.released || !session.tempoSettled) return
        running = null
        // the incoming song is the current one now; plan its own hand-over
        schedule()
    }

    private fun abortRunning(reason: String) {
        val session = running ?: return
        Timber.tag(Tag).d("transition ended early: %s", reason)
        running = null
        session.jobs.forEach { it.cancel() }
        session.inProcessor.finish()
        if (!session.released) {
            session.outProcessor.fadeOutNow()
            session.released = true
            scope.launch {
                delay(90)
                host.release(session.outgoing)
            }
        }
        // whatever plays next plays at the listener's own tempo
        val current = session.incoming.playbackParameters
        if (current.speed != session.base.speed) session.incoming.playbackParameters = session.base
        scope.launch { delay(150); schedule() }
    }

    private fun script(plan: CrossmixPlan, role: CrossmixRole, calibrationMs: Double, calibrationNanos: Long) =
        CrossmixTransitionAudioProcessor.Script(
            role = role,
            style = plan.style,
            variation = plan.variation,
            lengthBeats = plan.lengthBeats.toFloat(),
            anchorMs = if (role == CrossmixRole.Outgoing) plan.outStartMs.toDouble() else plan.inMediaAt(0f),
            mediaPerBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            musicalBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            endBeat = if (role == CrossmixRole.Outgoing) plan.lengthBeats + plan.style.tailBeats + 2f else plan.inSettledBeat,
            calibrationMs = calibrationMs,
            calibrationNanos = calibrationNanos,
        )

    private fun stillCurrent(player: ExoPlayer, item: MediaItem): Boolean =
        host.currentPlayer === player && player.currentMediaItem?.mediaId == item.mediaId

    /**
     * waits until the outgoing song reaches [targetMs] (its own clock, so pauses just wait longer).
     * false if the song changed or moved away meanwhile. [precise]: the last stretch is polled
     * every couple of ms, for the moment the next player starts.
     */
    private suspend fun waitForOutgoing(player: ExoPlayer, item: MediaItem, targetMs: Double, precise: Boolean = false): Boolean {
        while (scope.isActive) {
            if (!stillCurrent(player, item)) return false
            val remaining = targetMs - player.currentPosition
            if (remaining <= 0) return true
            val speed = player.playbackParameters.speed.coerceAtLeast(0.25f)
            val wall = remaining / speed
            delay(
                when {
                    !player.isPlaying -> 250L
                    wall > 3_000 -> min(1_000L, (wall - 2_000).toLong())
                    wall > 120 -> (wall / 3).toLong().coerceAtLeast(10L)
                    precise -> 2L
                    else -> wall.toLong().coerceAtLeast(1L)
                }
            )
        }
        return false
    }

    private companion object {
        const val Tag = "Crossmix"
        const val MinTrackMs = 45_000L
        /** the next song is prepared this far ahead of its first move */
        const val ArmLeadMs = 9_000.0
        /** too close to plan properly: let this one play through */
        const val MinArmLeadMs = 2_500.0
        /** the next song must be ready by this long before anything happens, or it's called off */
        const val GoMarginMs = 1_200.0
        const val BaseDelayMs = 40.0
        const val LockIntervalMs = 120L
        const val SpeedStep = 0.004f
    }
}
