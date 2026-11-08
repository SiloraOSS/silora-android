package org.siloserver.silo.common.admin

import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong
import org.siloserver.silo.admin.AdminDisplay
import org.siloserver.silo.admin.AdminPlaybackSession
import org.siloserver.silo.admin.AdminTask
import org.siloserver.silo.admin.humanizeKey
import org.siloserver.silo.model.request.MediaRequest
import org.siloserver.silo.util.parseRfc3339ToEpochMillis

/** Fork: words and numbers for the hand-built admin screens, shared by phone and TV. */
object AdminText {
    fun ago(timestamp: String?, now: Long = System.currentTimeMillis()): String? {
        val at = timestamp?.let(::parseRfc3339ToEpochMillis) ?: return null
        val seconds = (now - at) / 1000
        return when {
            seconds < 0 -> "in ${span(-seconds)}"
            seconds < 45 -> "just now"
            else -> "${span(seconds)} ago"
        }
    }

    fun until(timestamp: String?, now: Long = System.currentTimeMillis()): String? {
        val at = timestamp?.let(::parseRfc3339ToEpochMillis) ?: return null
        val seconds = (at - now) / 1000
        return if (seconds <= 0) "due now" else "in ${span(seconds)}"
    }

    /** `3 d 4 h`, `12 min`. */
    fun span(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = s / 86_400
        val hours = s % 86_400 / 3_600
        val minutes = s % 3_600 / 60
        return when {
            days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
            hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
            minutes > 0 -> "$minutes min"
            else -> "$s s"
        }
    }

    fun uptime(startedAt: String?): String? {
        val at = startedAt?.let(::parseRfc3339ToEpochMillis) ?: return null
        return span((System.currentTimeMillis() - at) / 1000)
    }

    /** `1:02:03` or `4:05`. */
    fun clock(seconds: Double): String {
        val total = seconds.roundToLong().coerceAtLeast(0)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun dateTime(timestamp: String?): String? {
        val at = timestamp?.let(::parseRfc3339ToEpochMillis) ?: return null
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
    }

    fun time(timestamp: String?): String? {
        val at = timestamp?.let(::parseRfc3339ToEpochMillis) ?: return null
        return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(at))
    }

    fun count(value: Long): String = "%,d".format(value)

    fun bytes(value: Long): String = AdminDisplay.humanBytes(value)

    fun gigabytes(value: Double): String = if (value >= 1024) "%.1f TB".format(value / 1024) else "%.0f GB".format(value)

    fun bitrate(kbps: Long?): String? = kbps?.takeIf { it > 0 }?.let {
        if (it >= 1000) "%.1f Mbps".format(it / 1000.0) else "$it kbps"
    }

    fun bitsPerSecond(bps: Long): String = when {
        bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.0f kbps".format(bps / 1_000.0)
        else -> "$bps bps"
    }

    /** "ed · Living room TV · Direct play · 1080p" */
    fun sessionDetail(session: AdminPlaybackSession): String = listOfNotNull(
        listOfNotNull(session.username.takeIf { it.isNotBlank() }, session.profileName?.takeIf { it.isNotBlank() && it != session.username })
            .joinToString(" / ").takeIf { it.isNotBlank() },
        (session.clientLabel ?: session.clientName)?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    fun sessionStream(session: AdminPlaybackSession): String = listOfNotNull(
        session.methodLabel.takeIf { it.isNotBlank() },
        if (session.isTranscoding) {
            listOfNotNull(session.sourceVideoResolution, session.targetResolution).distinct().joinToString(" → ").takeIf { it.isNotBlank() }
        } else session.sourceVideoResolution,
        session.transcodeHwAccel?.takeIf { session.isTranscoding && it.isNotBlank() }?.let { humanizeKey(it) },
        bitrate(session.streamBitrateKbps),
        session.streamLocation?.takeIf { it == "remote" }?.let { "Remote" },
    ).joinToString(" · ")

    fun sessionProgress(session: AdminPlaybackSession): String {
        val at = clock(session.positionSeconds)
        val length = session.fileDuration?.takeIf { it > 0 }?.let { clock(it.toDouble()) }
        return (if (length != null) "$at / $length" else at) + if (session.isPaused) " · Paused" else ""
    }

    fun requestLine(request: MediaRequest, usernames: Map<String, String>): String = listOfNotNull(
        if (request.mediaType == "series") "Series" else "Movie",
        request.year?.toString(),
        request.requestedByUserId?.let { usernames[it] ?: "user $it" }?.let { "by $it" },
        ago(request.createdAt),
    ).joinToString(" · ")

    fun requestStatus(request: MediaRequest): String = when {
        request.outcome == "declined" -> "Declined" + request.outcomeReason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        request.outcome == "cancelled" -> "Cancelled"
        request.outcome == "failed" -> "Failed" + request.lastError.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        else -> humanizeKey(request.status)
    }

    fun canApprove(request: MediaRequest) = request.isActive && request.status == "pending"
    fun canDecline(request: MediaRequest) = request.isActive && request.status == "pending"
    fun canRetry(request: MediaRequest) = request.outcome == "failed"
    fun canCancel(request: MediaRequest) = request.isActive && request.status != "completed" && request.status != "pending"

    fun taskLine(task: AdminTask): String {
        if (task.isRunning) {
            val pct = task.progress.coerceIn(0.0, 100.0).toInt()
            return if (task.state == "cancelling") "Cancelling…" else "Running · $pct%"
        }
        val last = task.lastExecution
        val lastText = last?.let {
            val status = when (it.status) { "completed" -> "Finished"; "failed" -> "Failed"; "cancelled" -> "Cancelled"; else -> humanizeKey(it.status) }
            listOfNotNull(status, ago(it.completedAt ?: it.startedAt)).joinToString(" ")
        }
        val next = until(task.nextRunAt)?.let { "next $it" } ?: if (task.manualOnly) "manual only" else null
        return listOfNotNull(lastText, next).joinToString(" · ").ifBlank { "Not run yet" }
    }

    /** 0..1 for a running task; the server reports 0..100. */
    fun taskProgress(task: AdminTask): Float = (task.progress / 100).toFloat().coerceIn(0f, 1f)
}
