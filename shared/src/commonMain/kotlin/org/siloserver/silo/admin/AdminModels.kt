package org.siloserver.silo.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.siloserver.silo.model.request.MediaRequest

/*
 * Fork: typed views of the admin responses the hand-built screens show
 * (dashboard, now playing, request moderation, tasks and scans, logs). Every
 * field has a default, so a server that adds or drops a field still decodes;
 * AdminHandBuiltContractTest checks the bundled spec still has each one.
 */

@Serializable
data class AdminPageInfo(
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
) {
    val cursor: String? get() = nextCursor?.takeIf { hasMore && it.isNotBlank() }
}

@Serializable
data class AdminCollection<T>(
    val items: List<T> = emptyList(),
    val page: AdminPageInfo? = null,
)

@Serializable
data class AdminHealthComponent(
    val ok: Boolean = false,
    val configured: Boolean = true,
    @SerialName("latency_ms") val latencyMs: Double? = null,
)

@Serializable
data class AdminServerHealth(
    @SerialName("errors_24h") val errors24h: Int = 0,
    @SerialName("warnings_24h") val warnings24h: Int = 0,
    val postgres: AdminHealthComponent? = null,
    val redis: AdminHealthComponent? = null,
)

@Serializable
data class AdminServerStatus(
    @SerialName("started_at") val startedAt: String = "",
    val health: AdminServerHealth = AdminServerHealth(),
    @SerialName("restart_required") val restartRequired: Boolean = false,
    @SerialName("restart_required_reason") val restartRequiredReason: String? = null,
    @SerialName("restart_required_reasons") val restartRequiredReasons: List<String> = emptyList(),
    @SerialName("restart_requested") val restartRequested: Boolean = false,
)

@Serializable
data class AdminBuildInfo(
    val available: Boolean = false,
    val display: String = "",
    val revision: String = "",
    @SerialName("build_number") val buildNumber: Long = 0,
)

@Serializable
data class AdminDashboardStats(
    @SerialName("active_streams") val activeStreams: Int = 0,
    @SerialName("total_movies") val totalMovies: Long = 0,
    @SerialName("total_shows") val totalShows: Long = 0,
    @SerialName("total_items") val totalItems: Long = 0,
    @SerialName("total_files") val totalFiles: Long = 0,
    @SerialName("total_storage_bytes") val totalStorageBytes: Long = 0,
    @SerialName("total_users") val totalUsers: Long = 0,
)

@Serializable
data class AdminDiskStats(
    val path: String = "",
    val role: String? = null,
    @SerialName("used_gb") val usedGb: Double = 0.0,
    @SerialName("total_gb") val totalGb: Double = 0.0,
    val unavailable: Boolean = false,
)

@Serializable
data class AdminSystemStats(
    val cores: Int = 0,
    @SerialName("cpu_pct") val cpuPct: Int = 0,
    val load1: Double = 0.0,
    @SerialName("mem_total_mb") val memTotalMb: Long = 0,
    @SerialName("mem_used_mb") val memUsedMb: Long = 0,
    @SerialName("net_rx_bps") val netRxBps: Long = 0,
    @SerialName("net_tx_bps") val netTxBps: Long = 0,
    val disks: List<AdminDiskStats> = emptyList(),
)

@Serializable
data class AdminGpuStats(
    val device: String = "",
    val vendor: String? = null,
    val sessions: Int = 0,
    @SerialName("total_busy_pct") val totalBusyPct: Int? = null,
    @SerialName("video_busy_pct") val videoBusyPct: Int? = null,
)

@Serializable
data class AdminSystemResources(
    val available: Boolean = false,
    val stale: Boolean = false,
    val system: AdminSystemStats? = null,
    val gpu: List<AdminGpuStats> = emptyList(),
)

@Serializable
data class AdminRequestCounts(
    @SerialName("needs_approval") val needsApproval: Int = 0,
    @SerialName("in_progress") val inProgress: Int = 0,
    val failed: Int = 0,
    val done: Int = 0,
)

@Serializable
data class AdminPlaybackSession(
    @SerialName("session_id") val sessionId: String = "",
    val username: String = "",
    @SerialName("profile_name") val profileName: String? = null,
    @SerialName("media_title") val mediaTitle: String = "",
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("episode_name") val episodeName: String? = null,
    @SerialName("client_label") val clientLabel: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_ip") val clientIp: String? = null,
    @SerialName("play_method") val playMethod: String = "",
    @SerialName("effective_play_method") val effectivePlayMethod: String? = null,
    @SerialName("video_decision") val videoDecision: String? = null,
    @SerialName("audio_decision") val audioDecision: String? = null,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("file_duration") val fileDuration: Long? = null,
    @SerialName("stream_bitrate_kbps") val streamBitrateKbps: Long? = null,
    @SerialName("source_video_resolution") val sourceVideoResolution: String? = null,
    @SerialName("target_resolution") val targetResolution: String? = null,
    @SerialName("transcode_hw_accel") val transcodeHwAccel: String? = null,
    @SerialName("stream_location") val streamLocation: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("has_playback_control") val hasPlaybackControl: Boolean = false,
    @SerialName("started_at") val startedAt: String = "",
) {
    val displayTitle: String
        get() = if (!seriesName.isNullOrBlank()) seriesName else mediaTitle

    val episodeLine: String?
        get() {
            if (seriesName.isNullOrBlank()) return null
            val code = if (seasonNumber != null && episodeNumber != null) "S${seasonNumber}E$episodeNumber" else null
            return listOfNotNull(code, episodeName?.takeIf { it.isNotBlank() } ?: mediaTitle.takeIf { it != seriesName })
                .joinToString(" · ").takeIf { it.isNotBlank() }
        }

    /** 0..1, or null when the file's length is unknown. */
    val progress: Float?
        get() = fileDuration?.takeIf { it > 0 }?.let { (positionSeconds / it).toFloat().coerceIn(0f, 1f) }

    val isTranscoding: Boolean
        get() = (effectivePlayMethod ?: playMethod).contains("transcode", ignoreCase = true)

    val methodLabel: String
        get() = when ((effectivePlayMethod ?: playMethod).lowercase()) {
            "directplay", "direct_play", "direct" -> "Direct play"
            "directstream", "direct_stream", "remux" -> "Direct stream"
            "transcode" -> "Transcode"
            "" -> ""
            else -> humanizeKey(effectivePlayMethod ?: playMethod)
        }
}

@Serializable
data class AdminTaskExecution(
    val status: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class AdminTaskTrigger(
    val type: String = "",
    @SerialName("interval_ms") val intervalMs: Long? = null,
    @SerialName("time_of_day") val timeOfDay: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int? = null,
)

@Serializable
data class AdminTask(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val state: String = "idle",
    val progress: Double = 0.0,
    @SerialName("manual_only") val manualOnly: Boolean = false,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_execution") val lastExecution: AdminTaskExecution? = null,
    val triggers: List<AdminTaskTrigger> = emptyList(),
) {
    val isRunning: Boolean get() = state == "running" || state == "cancelling"
}

@Serializable
data class AdminLibrary(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val enabled: Boolean = true,
    @SerialName("last_scanned_at") val lastScannedAt: String? = null,
    @SerialName("scan_warning_message") val scanWarningMessage: String? = null,
)

@Serializable
data class AdminOperationalLog(
    val id: String = "",
    val timestamp: String = "",
    val level: String = "",
    val component: String = "",
    val message: String = "",
    @SerialName("node_id") val nodeId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val attrs: JsonObject? = null,
)

@Serializable
data class AdminAuditLog(
    val id: String = "",
    val timestamp: String = "",
    val action: String? = null,
    val method: String = "",
    val path: String = "",
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("client_ip") val clientIp: String = "",
    @SerialName("target_type") val targetType: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
)

@Serializable
data class AdminUserSummary(
    val id: String = "",
    val username: String = "",
)

typealias AdminMediaRequest = MediaRequest
