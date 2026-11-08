package org.siloserver.silo.common.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.KSerializer
import org.siloserver.silo.admin.AdminAuditLog
import org.siloserver.silo.admin.AdminBuildInfo
import org.siloserver.silo.admin.AdminDashboardStats
import org.siloserver.silo.admin.AdminLibrary
import org.siloserver.silo.admin.AdminOperation
import org.siloserver.silo.admin.AdminOperationalLog
import org.siloserver.silo.admin.AdminPlaybackSession
import org.siloserver.silo.admin.AdminRequestCounts
import org.siloserver.silo.admin.AdminSchema
import org.siloserver.silo.admin.AdminServerStatus
import org.siloserver.silo.admin.AdminSpec
import org.siloserver.silo.admin.AdminSystemResources
import org.siloserver.silo.admin.AdminTask
import org.siloserver.silo.admin.AdminUserSummary
import org.siloserver.silo.model.request.MediaRequest

/**
 * Fork: keeps the hand-built admin screens in step with the server. The
 * fork-release sync regenerates the bundled spec from silo-server; when an
 * operation these screens call is gone, or a field they read or send was
 * renamed or removed, this fails and names it, so the screen is updated in
 * the same sync.
 */
class AdminHandBuiltContractTest {
    private val spec = AdminSpec.parse(File("src/androidMain/assets/${AdminSpecLoader.ASSET}").readText())
    private val byId = spec.operations.associateBy { it.operationId }

    private fun op(id: String): AdminOperation = byId[id] ?: fail("The server no longer offers $id")

    private fun assertFields(id: String, schema: AdminSchema?, serializer: KSerializer<*>, optional: Set<String> = emptySet()) {
        val properties = schema?.properties?.keys ?: fail("$id has no response schema")
        val missing = (0 until serializer.descriptor.elementsCount)
            .map { serializer.descriptor.getElementName(it) }
            .filterNot { it in properties || it in optional }
        assertTrue(missing.isEmpty(), "$id no longer returns ${missing.joinToString()} (read by ${serializer.descriptor.serialName})")
    }

    private fun assertRecord(id: String, serializer: KSerializer<*>, optional: Set<String> = emptySet()) =
        assertFields(id, op(id).successSchema, serializer, optional)

    private fun assertList(id: String, serializer: KSerializer<*>, optional: Set<String> = emptySet()) =
        assertFields(id, op(id).successSchema?.property("items")?.items, serializer, optional)

    private fun assertBody(id: String, vararg fields: String) {
        val body = op(id).requestBody?.schema?.properties?.keys.orEmpty()
        val missing = fields.filterNot { it in body }
        assertTrue(missing.isEmpty(), "$id no longer takes ${missing.joinToString()}")
    }

    private fun assertQuery(id: String, vararg names: String) {
        val params = op(id).queryParams.map { it.name }
        val missing = names.filterNot { it in params }
        assertTrue(missing.isEmpty(), "$id no longer takes the query ${missing.joinToString()}")
    }

    @Test
    fun everyOperationTheScreensCallStillExists() {
        val missing = AdminOps.ALL.filterNot { it in byId }
        assertTrue(missing.isEmpty(), "Gone from the server: $missing")
    }

    @Test
    fun dashboardFieldsStillExist() {
        assertRecord(AdminOps.SERVER_STATUS, AdminServerStatus.serializer())
        assertRecord(AdminOps.BUILD, AdminBuildInfo.serializer())
        assertRecord(AdminOps.STATS, AdminDashboardStats.serializer())
        assertRecord(AdminOps.RESOURCES, AdminSystemResources.serializer())
        assertRecord(AdminOps.REQUEST_COUNTS, AdminRequestCounts.serializer())
        assertList(AdminOps.SESSIONS, AdminPlaybackSession.serializer())
        for (command in listOf(AdminOps.SESSION_PAUSE, AdminOps.SESSION_RESUME, AdminOps.SESSION_STOP)) {
            assertBody(command, "command_id", "sequence")
        }
        assertBody(AdminOps.SESSION_MESSAGE, "command_id", "sequence", "message", "title")
    }

    @Test
    fun moderationFieldsStillExist() {
        // MediaRequest is the app's own model; check the fields moderation shows.
        val request = op(AdminOps.REQUESTS).successSchema?.property("items")?.items?.properties?.keys.orEmpty()
        val shown = listOf("id", "title", "year", "media_type", "poster_path", "status", "outcome", "requested_by_user_id", "created_at", "last_error", "outcome_reason")
        assertTrue(request.containsAll(shown), "listAdminRequests no longer returns ${shown - request}")
        assertQuery(AdminOps.REQUESTS, "view", "limit")
        val views = op(AdminOps.REQUESTS).queryParams.first { it.name == "view" }.schema?.enumValues.orEmpty()
        assertTrue(AdminRequestView.entries.all { it.key in views }, "Request views changed: $views")
        assertBody(AdminOps.REQUEST_DECLINE, "reason")
        assertList(AdminOps.USERS, AdminUserSummary.serializer())
        // Decoding needs every non-optional MediaRequest field.
        val required = (0 until MediaRequest.serializer().descriptor.elementsCount)
            .filterNot { MediaRequest.serializer().descriptor.isElementOptional(it) }
            .map { MediaRequest.serializer().descriptor.getElementName(it) }
        assertTrue(request.containsAll(required), "listAdminRequests no longer returns ${required - request}")
    }

    @Test
    fun tasksAndScansFieldsStillExist() {
        assertList(AdminOps.TASKS, AdminTask.serializer())
        assertList(AdminOps.LIBRARIES, AdminLibrary.serializer())
        assertBody(AdminOps.SCAN, "library_id")
        assertBody(AdminOps.SCAN_CANCEL, "library_id")
        val states = op(AdminOps.TASKS).successSchema?.property("items")?.items?.property("state")?.enumValues.orEmpty()
        assertTrue(states.containsAll(listOf("idle", "running", "cancelling")), "Task states changed: $states")
    }

    @Test
    fun logFieldsStillExist() {
        assertList(AdminOps.LOGS_APP, AdminOperationalLog.serializer())
        assertList(AdminOps.LOGS_AUDIT, AdminAuditLog.serializer())
        assertQuery(AdminOps.LOGS_APP, "level", "q", "limit")
        assertQuery(AdminOps.LOGS_AUDIT, "path_prefix", "limit")
    }
}
