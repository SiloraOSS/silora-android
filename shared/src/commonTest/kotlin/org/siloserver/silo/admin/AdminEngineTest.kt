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
    fun emptiedFieldsClearInsteadOfBeingLeftOut() {
        val form = AdminForm(op("PATCH", "users/{id}"), mapOf("id" to "5"))
        val prefill = Json.parseToJsonElement("""{"role":"user","max_streams":2,"tags":["a"]}""")
        val values = form.initialValues(prefill).toMutableMap()
        values["max_streams"] = ""
        values["tags"] = ""
        val body = form.build(values).getOrThrow().body!!.jsonObject
        assertEquals(kotlinx.serialization.json.JsonNull, body["max_streams"], "a nullable field the user emptied sends null")
        assertEquals(kotlinx.serialization.json.JsonArray(emptyList()), body["tags"], "an emptied list sends []")
        assertTrue("policy" !in body, "an untouched field is still left out")
    }

    private val formSpec = AdminSpec.parse(
        """
        {"info":{"version":"2"},
         "paths":{
          "/api/v2/admin/settings":{"put":{"operationId":"updateAdminSettings","requestBody":{"required":true,"mediaType":"application/json",
             "schema":{"type":"object","required":["values"],"properties":{"values":{"type":"object","additionalProperties":{"type":"string"}}}}},"responses":{"200":{}}}},
          "/api/v2/admin/settings/{key}":{"put":{"operationId":"updateAdminSetting","parameters":[{"name":"key","in":"path","required":true}],
             "requestBody":{"required":true,"mediaType":"application/json","schema":{"type":"object","required":["value"],"properties":{"value":{"type":"string"}}}},"responses":{"200":{}}}},
          "/api/v2/admin/accounts":{"post":{"operationId":"createAdminAccount","requestBody":{"required":true,"mediaType":"application/json",
             "schema":{"type":"object","properties":{"username":{"type":"string"},"password":{"type":"string"},"admin_token":{"type":"string"},"token_ttl":{"type":"string"}}}},"responses":{"201":{}}}},
          "/api/v2/admin/sessions/{session_id}/pause":{"post":{"operationId":"pauseAdminPlaybackSession","parameters":[{"name":"session_id","in":"path","required":true}],
             "requestBody":{"required":true,"mediaType":"application/json","schema":{"type":"object","required":["command_id","sequence"],
               "properties":{"command_id":{"type":"string"},"sequence":{"type":"integer"},"reason":{"type":"string"}}}},"responses":{"200":{}}}}
         },
         "schemas":{}}
        """.trimIndent(),
    )
    private fun fop(method: String, path: String) = AdminCatalog(formSpec).operation(method, "/api/v2/admin/$path")!!

    @Test
    fun aRequiredTextCanBeSetEmpty() {
        val form = AdminForm(fop("PUT", "settings/{key}"), mapOf("key" to "ui.banner"))
        assertEquals(JsonPrimitive(""), form.build(mapOf("value" to "")).getOrThrow().body!!.jsonObject["value"])
    }

    @Test
    fun mapsAreEditedAsKeyValueLines() {
        val form = AdminForm(fop("PUT", "settings"))
        val field = form.fields.single()
        assertEquals(AdminFieldKind.KeyValues, field.kind)
        val values = form.initialValues(Json.parseToJsonElement("""{"values":{"a.b":"1","c":"x=y"}}"""))
        assertEquals("a.b=1\nc=x=y", values["values"])
        val body = form.build(values).getOrThrow().body!!.jsonObject
        assertEquals(Json.parseToJsonElement("""{"a.b":"1","c":"x=y"}"""), body["values"])
        assertTrue(form.build(mapOf("values" to "no equals sign")).isFailure)
    }

    @Test
    fun credentialFieldsAreMaskedByName() {
        val kinds = AdminForm(fop("POST", "accounts")).fields.associate { it.key to it.kind }
        assertEquals(AdminFieldKind.Secret, kinds["password"])
        assertEquals(AdminFieldKind.Secret, kinds["admin_token"])
        assertEquals(AdminFieldKind.Text, kinds["username"])
        assertEquals(AdminFieldKind.Text, kinds["token_ttl"])
    }

    @Test
    fun aHeldCommandIdentityIsReused() {
        val form = AdminForm(fop("POST", "sessions/{session_id}/pause"), mapOf("session_id" to "s1"))
        assertTrue(form.sendsCommandIdentity)
        assertEquals(listOf("reason"), form.fields.map { it.key }, "the identity isn't a form field")
        val identity = AdminForm.newCommandIdentity()
        val first = form.build(identity).getOrThrow().body!!.jsonObject
        val again = form.build(identity).getOrThrow().body!!.jsonObject
        assertEquals(first, again, "a resend carries the same command_id and sequence")
        val serverLatest = 4_000_000_000_000L
        AdminForm.raiseCommandSequenceFloor(serverLatest)
        val next = AdminForm.newCommandIdentity()[AdminForm.COMMAND_SEQUENCE_KEY]!!.toLong()
        assertTrue(next > serverLatest, "after a stale refusal the next sequence goes above the server's")
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

    private val bindingSpec = AdminSpec.parse(
        """
        {"info":{"version":"2","source":"abc123 2026-11-11"},
         "paths":{
          "/api/v2/admin/devices":{"get":{"operationId":"listAdminDevices","responses":{"200":{"schema":{"${'$'}ref":"#/schemas/Page"}}}}},
          "/api/v2/admin/devices/{user_id}/{device_id}":{"get":{"operationId":"getAdminDevice","parameters":[
             {"name":"user_id","in":"path","required":true},{"name":"device_id","in":"path","required":true}],"responses":{"200":{}}}},
          "/api/v2/admin/subtitle-providers":{"get":{"operationId":"listAdminSubtitleProviders","responses":{"200":{"schema":{"${'$'}ref":"#/schemas/Page"}}}}},
          "/api/v2/admin/subtitle-providers/{provider}":{"put":{"operationId":"updateAdminSubtitleProvider","parameters":[
             {"name":"provider","in":"path","required":true}],"responses":{"200":{}}}},
          "/api/v2/admin/collections/{id}/items":{"get":{"operationId":"listAdminCollectionItems","parameters":[
             {"name":"id","in":"path","required":true}],"responses":{"200":{"schema":{"${'$'}ref":"#/schemas/Page"}}}}},
          "/api/v2/admin/collections/{id}/items/{item_id}":{"delete":{"operationId":"removeAdminCollectionItem","parameters":[
             {"name":"id","in":"path","required":true},{"name":"item_id","in":"path","required":true}],"responses":{"204":{}}}},
          "/api/v2/admin/users/{id}":{"get":{"operationId":"getAdminUser","parameters":[{"name":"id","in":"path","required":true}],"responses":{"200":{}}}},
          "/api/v2/admin/users/{user_id}/login-sessions":{"get":{"operationId":"listAdminUserLoginSessions","parameters":[
             {"name":"user_id","in":"path","required":true}],"responses":{"200":{"schema":{"${'$'}ref":"#/schemas/Page"}}}}},
          "/api/v2/admin/jobs/{id}/artifact":{"get":{"operationId":"downloadAdminJobArtifact","x-silo-raw-protocol":"job-artifact",
             "parameters":[{"name":"id","in":"path","required":true}],"responses":{"200":{"mediaType":"application/gzip"}}}},
          "/api/v2/admin/collections/{id}/poster":{"put":{"operationId":"uploadAdminCollectionPoster","parameters":[{"name":"id","in":"path","required":true}],
             "requestBody":{"required":true,"mediaType":"multipart/form-data","schema":{"type":"object"}},"responses":{"200":{}}}},
          "/api/v2/admin/nodes/{id}":{"put":{"operationId":"updateAdminNode","x-silo-guarded":true,"parameters":[{"name":"id","in":"path","required":true}],"responses":{"200":{}}}}
         },
         "schemas":{"Page":{"type":"object","properties":{"items":{"type":"array","items":{"type":"object"}}}}}}
        """.trimIndent(),
    )
    private val bindingCatalog = AdminCatalog(bindingSpec)
    private fun bop(method: String, path: String) = bindingCatalog.operation(method, "/api/v2/admin/$path")!!

    @Test
    fun itemActionsBindEveryParamTheItemCanFill() {
        fun actions(list: String, pathValues: Map<String, String>, item: String) =
            bindingCatalog.itemActions(bop("GET", list), pathValues, Json.parseToJsonElement(item))
                .map { it.operation.operationId to it.pathValues }
        assertEquals(
            listOf("getAdminDevice" to mapOf("user_id" to "u1", "device_id" to "d1")),
            actions("devices", emptyMap(), """{"user_id":"u1","device_id":"d1","name":"TV"}"""),
        )
        assertEquals(
            listOf("updateAdminSubtitleProvider" to mapOf("provider" to "opensubtitles")),
            actions("subtitle-providers", emptyMap(), """{"provider_name":"opensubtitles"}"""),
        )
        assertEquals(
            listOf("removeAdminCollectionItem" to mapOf("id" to "c1", "item_id" to "m9")),
            actions("collections/{id}/items", mapOf("id" to "c1"), """{"collection_id":"c1","media_item_id":"m9"}"""),
        )
    }

    @Test
    fun detailActionsIgnoreParamNames() {
        val actions = bindingCatalog.detailActions(bop("GET", "users/{id}"), mapOf("id" to "7"))
        assertEquals(listOf("listAdminUserLoginSessions" to mapOf("user_id" to "7")), actions.map { it.operation.operationId to it.pathValues })
    }

    @Test
    fun downloadsUploadsAndGuardsComeFromTheSpec() {
        assertNull(bindingCatalog.operation("GET", "/api/v2/admin/jobs/{id}/artifact"), "a download isn't offered")
        assertNull(bindingCatalog.operation("PUT", "/api/v2/admin/collections/{id}/poster"), "an upload isn't offered")
        assertTrue(bop("PUT", "nodes/{id}").needsIfMatch, "x-silo-guarded requires If-Match without a header param")
        assertEquals("abc123 2026-11-11", bindingSpec.source)
        assertEquals("c9", AdminCatalog.versionTagOf(Json.parseToJsonElement("""{"id":"n1","config_etag":"c9"}""")))
    }

    @Test
    fun deletesAndUpdatesAreFlagged() {
        assertTrue(op("DELETE", "users/{id}").isDestructive)
        assertTrue(op("PATCH", "users/{id}").needsConfirmation, "every change is confirmed")
        assertTrue(!op("GET", "users/{id}").needsConfirmation && !op("GET", "users/{id}").isDestructive)
        assertTrue(bop("PUT", "nodes/{id}").needsConfirmation)
        assertTrue(op("PATCH", "users/{id}").needsIfMatch)
        assertEquals("Update user", op("PATCH", "users/{id}").title)
    }

    @Test
    fun fileUploadsRequireWebAdminInsteadOfSendingEmptyBodies() {
        for (mediaType in listOf("multipart/form-data", "application/octet-stream")) {
            val upload = AdminSpec.parse("""{"paths":{"/api/v2/admin/uploads":{"post":{"operationId":"upload","requestBody":{"required":true,"mediaType":"$mediaType","schema":{"type":"object"}}}}}}""").operations.single()
            val error = AdminForm(upload).build(emptyMap()).exceptionOrNull()
            assertEquals("This operation requires a file upload. Use the web admin.", error?.message)
        }
    }
}
