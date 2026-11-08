package org.siloserver.silo.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.siloserver.silo.network.apiv2.AdminV2Api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminEngineTest {
    private val spec = AdminSpec.parse(
        """
        {"info":{"version":"2.0"},
         "paths":{
          "/api/v2/admin/users":{"get":{"operationId":"listAdminUsers","parameters":[
             {"name":"limit","in":"query","schema":{"type":"integer"}},
             {"name":"cursor","in":"query","schema":{"type":"string"}}],
             "responses":{"200":{"schema":{"${'$'}ref":"#/schemas/UserPage"}}}}},
          "/api/v2/admin/users/{id}":{
             "get":{"operationId":"getAdminUser","parameters":[{"name":"id","in":"path","required":true}],
               "responses":{"200":{"schema":{"${'$'}ref":"#/schemas/User"}}}},
             "patch":{"operationId":"updateAdminUser","parameters":[
                 {"name":"id","in":"path","required":true},
                 {"name":"If-Match","in":"header","required":true}],
               "requestBody":{"required":true,"mediaType":"application/json","schema":{"${'$'}ref":"#/schemas/UserPatch"}},
               "responses":{"200":{"schema":{"${'$'}ref":"#/schemas/User"}}}},
             "delete":{"operationId":"deleteAdminUser","parameters":[{"name":"id","in":"path","required":true}],"responses":{"204":{}}}},
          "/api/v2/admin/users/{id}/profiles":{"get":{"operationId":"listAdminUserProfiles",
             "parameters":[{"name":"id","in":"path","required":true}],"responses":{"200":{"schema":{"${'$'}ref":"#/schemas/UserPage"}}}}}
         },
         "schemas":{
          "User":{"type":"object","properties":{"id":{"type":"integer"},"username":{"type":"string"},"role":{"type":"string","enum":["admin","user"]}}},
          "UserPage":{"type":"object","properties":{"items":{"type":"array","items":{"${'$'}ref":"#/schemas/User"}}}},
          "UserPatch":{"type":"object","required":["role"],"properties":{
             "role":{"type":"string","enum":["admin","user"]},
             "max_streams":{"type":["integer","null"]},
             "tags":{"type":"array","items":{"type":"string"}},
             "policy":{"type":"object","properties":{"downloads":{"type":"boolean"}}},
             "id":{"type":"integer","readOnly":true}}}
         }}
        """.trimIndent(),
    )
    private val catalog = AdminCatalog(spec)
    private fun op(method: String, path: String) = catalog.operation(method, "/api/v2/admin/$path")!!

    @Test
    fun listItemsOfferTheirOwnActions() {
        val list = op("GET", "users")
        assertTrue(list.returnsList)
        val item = Json.parseToJsonElement("""{"id":5,"username":"ed"}""")
        val actions = catalog.itemActions(list, emptyMap(), item).map { it.operation.operationId to it.pathValues }
        assertEquals(
            listOf(
                "getAdminUser" to mapOf("id" to "5"),
                "listAdminUserProfiles" to mapOf("id" to "5"),
                "updateAdminUser" to mapOf("id" to "5"),
                "deleteAdminUser" to mapOf("id" to "5"),
            ),
            actions,
        )
    }

    @Test
    fun formBuildsTypedBodyAndSkipsReadOnlyFields() {
        val form = AdminForm(op("PATCH", "users/{id}"), mapOf("id" to "5"))
        assertTrue(form.fields.none { it.key == "id" }, "read-only and fixed path fields are not edited")
        val prefill = Json.parseToJsonElement("""{"role":"user","max_streams":2,"tags":["a","b"],"policy":{"downloads":true}}""")
        val values = form.initialValues(prefill).toMutableMap()
        assertEquals("a\nb", values["tags"])
        values["max_streams"] = "4"
        val request = form.build(values).getOrThrow()
        assertEquals(mapOf("id" to "5"), request.pathValues)
        val body = request.body!!.jsonObject
        assertEquals(JsonPrimitive("user"), body["role"])
        assertEquals(JsonPrimitive(4L), body["max_streams"])
        assertEquals(JsonPrimitive(true), (body["policy"] as JsonObject)["downloads"])
        assertEquals(2, (body["tags"] as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun formRejectsMissingRequiredAndBadNumbers() {
        val form = AdminForm(op("PATCH", "users/{id}"), mapOf("id" to "5"))
        assertTrue(form.build(emptyMap()).isFailure)
        assertTrue(form.build(mapOf("role" to "user", "max_streams" to "lots")).isFailure)
        val unbound = AdminForm(op("GET", "users/{id}"))
        assertTrue(unbound.build(emptyMap()).isFailure, "a missing path value is an error")
    }

    @Test
    fun pathValuesAreEncodedAndRequired() {
        assertEquals("/api/v2/admin/users/a%2Fb", AdminV2Api.fillPath(op("GET", "users/{id}"), mapOf("id" to "a/b")))
        assertNull(AdminV2Api.fillPath(op("GET", "users/{id}"), emptyMap()))
    }

    @Test
    fun pagesFollowTheCursorOnlyWhileMoreRemain() {
        val more = Json.parseToJsonElement("""{"items":[{"id":1}],"page":{"has_more":true,"next_cursor":"c2"}}""")
        val last = Json.parseToJsonElement("""{"items":[{"id":1}],"page":{"has_more":false,"next_cursor":"c3"}}""")
        assertEquals("c2", AdminDisplay.page(more)?.nextCursor)
        assertNull(AdminDisplay.page(last)?.nextCursor)
        assertEquals("2026-11-08 14:03 UTC", AdminDisplay.formatValue("created_at", "2026-11-08T14:03:22Z"))
    }

    @Test
    fun deletesAndUpdatesAreFlagged() {
        assertTrue(op("DELETE", "users/{id}").isDestructive)
        assertTrue(op("PATCH", "users/{id}").needsIfMatch)
        assertEquals("Update user", op("PATCH", "users/{id}").title)
    }
}
