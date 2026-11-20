package org.siloserver.silo.admin

import org.siloserver.silo.network.SiloJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminPlaybackSessionTest {
    @Test
    fun currentServerUsesMediaTitleForTheEpisode() {
        val session = decodeSession(
            """
            {
              "series_title": "Harbour Lights",
              "media_title": "High Tide",
              "season_number": 2,
              "episode_number": 3
            }
            """,
        )

        assertEquals("Harbour Lights", session.displayTitle)
        assertEquals("S2E3 · High Tide", session.episodeLine)
    }

    @Test
    fun olderServerUsesTheSeparateEpisodeName() {
        val session = decodeSession(
            """
            {
              "series_name": "Harbour Lights",
              "media_title": "Harbour Lights",
              "episode_name": "High Tide",
              "season_number": 2,
              "episode_number": 3
            }
            """,
        )

        assertEquals("Harbour Lights", session.displayTitle)
        assertEquals("S2E3 · High Tide", session.episodeLine)
    }

    @Test
    fun standaloneMediaUsesItsOwnTitle() {
        val session = decodeSession(
            """{"media_type":"movie","media_title":"High Tide"}""",
        )

        assertEquals("High Tide", session.displayTitle)
        assertNull(session.episodeLine)
    }

    private fun decodeSession(payload: String): AdminPlaybackSession =
        SiloJson.decodeFromJsonElement(AdminPlaybackSession.serializer(), SiloJson.parseToJsonElement(payload))
}
