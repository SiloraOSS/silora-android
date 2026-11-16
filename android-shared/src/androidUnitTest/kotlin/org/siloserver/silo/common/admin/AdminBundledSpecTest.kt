package org.siloserver.silo.common.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.siloserver.silo.admin.AdminCatalog
import org.siloserver.silo.admin.AdminCategory
import org.siloserver.silo.admin.AdminFieldKind
import org.siloserver.silo.admin.AdminForm
import org.siloserver.silo.admin.AdminSpec

/** Fork: the console must open every operation in the spec it ships with. */
class AdminBundledSpecTest {
    private val catalog = AdminCatalog(
        AdminSpec.parse(File("src/androidMain/assets/${AdminSpecLoader.ASSET}").readText()),
    )

    @Test
    fun everyOperationBuildsAForm() {
        val ops = catalog.spec.operations
        assertTrue(ops.size > 300, "expected the full admin surface, got ${ops.size}")
        for (op in ops) {
            val form = AdminForm(op)
            form.initialValues()
            form.build(emptyMap())
        }
    }

    @Test
    fun everyGroupHasAreasAndTheShortcutsResolve() {
        for (category in AdminCategory.entries) {
            assertTrue(catalog.resourcesIn(category).isNotEmpty(), "$category is empty")
        }
        assertTrue(catalog.quickActions.size >= 6, "shortcuts: ${catalog.quickActions.map { it.title }}")
    }

    @Test
    fun downloadsUploadsAndTheLogSocketAreLeftOut() {
        assertTrue(catalog.operations.all { it.isSupported })
        assertTrue(catalog.operations.size < catalog.spec.operations.size, "the spec has uploads and downloads to leave out")
        assertTrue(catalog.operation("GET", "/api/v2/admin/logs/ws") == null)
        assertTrue(catalog.spec.source != null, "admin-spec.py stamps the contract's source")
    }

    @Test
    fun aDeviceOpensWithItsReadAction() {
        val list = catalog.operation("GET", "/api/v2/admin/devices")!!
        val actions = catalog.itemActions(list, emptyMap(), Json.parseToJsonElement("""{"user_id":"7","device_id":"tv-1"}"""))
        val read = actions.single { it.operation.operationId == "getAdminDevice" }
        assertEquals(mapOf("user_id" to "7", "device_id" to "tv-1"), read.pathValues)
    }

    @Test
    fun guardedChangesNeedAVersionTagAndTheSettingsMapIsEditable() {
        val guarded = catalog.operation("PUT", "/api/v2/admin/nodes/{id}")!!
        assertTrue(guarded.needsIfMatch && guarded.guarded)
        val settings = AdminForm(catalog.operation("PUT", "/api/v2/admin/settings")!!)
        assertEquals(AdminFieldKind.KeyValues, settings.fields.single().kind)
    }
}
