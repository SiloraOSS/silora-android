package org.siloserver.silo.common.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.siloserver.silo.admin.AdminCatalog
import org.siloserver.silo.admin.AdminCategory
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
}
