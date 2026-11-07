package org.siloserver.silo.tv.ui.screens.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvSearchVoiceControllerTest {
    private class Session : TvSpeechSession {
        lateinit var listener: TvSpeechListener
        var closed = false
        override fun start(listener: TvSpeechListener) { this.listener = listener }
        override fun close() { closed = true }
    }
    private class Fixture(scope: CoroutineScope) {
        val sessions = mutableListOf<Session>()
        val queries = mutableListOf<String>()
        val controller = TvSearchVoiceController(scope, { Session().also(sessions::add) }, { queries.add(it) })
        val latest get() = sessions.last()
    }

    @Test fun partialsStayInTheOverlayAndOnlyFinalResultsSubmit() = runTest {
        val f = Fixture(this)
        f.controller.start()
        assertEquals(TvVoicePhase.Preparing, f.controller.state.value?.phase)
        f.latest.listener.ready()
        f.latest.listener.partial("star")
        assertEquals("star", f.controller.state.value?.transcript)
        assertTrue(f.queries.isEmpty())
        f.latest.listener.processing()
        assertEquals(TvVoicePhase.Processing, f.controller.state.value?.phase)
        f.latest.listener.result("  star\nwars  ")
        assertEquals(listOf("star wars"), f.queries)
        assertNull(f.controller.state.value)
        assertTrue(f.latest.closed)
    }

    @Test fun cancellationIgnoresAllLateCallbacksWithoutChangingTheQuery() = runTest {
        val f = Fixture(this)
        f.controller.start()
        f.controller.cancel()
        f.latest.listener.ready()
        f.latest.listener.partial("late")
        f.latest.listener.result("late result")
        f.latest.listener.error("late error")
        assertNull(f.controller.state.value)
        assertTrue(f.latest.closed)
        assertTrue(f.queries.isEmpty())
    }

    @Test fun callbacksFromThePreviousAttemptCannotOverwriteARetry() = runTest {
        val f = Fixture(this)
        f.controller.start()
        val previous = f.latest
        previous.listener.error("No speech")
        assertEquals(TvVoicePhase.Error, f.controller.state.value?.phase)
        f.controller.start()
        previous.listener.result("wrong query")
        previous.listener.error("stale failure")
        f.latest.listener.result("correct query")
        assertEquals(listOf("correct query"), f.queries)
    }

    @Test fun permissionIsRequestedBeforeAnyRecognitionSessionExists() = runTest {
        val f = Fixture(this)
        f.controller.awaitPermission()
        assertEquals(TvVoicePhase.Permission, f.controller.state.value?.phase)
        assertTrue(f.sessions.isEmpty())
        f.controller.permissionDenied(permanent = false)
        assertEquals(TvVoicePhase.Error, f.controller.state.value?.phase)
        assertFalse(f.controller.state.value!!.openSettings)
        f.controller.permissionDenied(permanent = true)
        assertTrue(f.controller.state.value!!.openSettings)
        assertTrue(f.queries.isEmpty())
    }

    @Test fun missingProviderOffersTypingWithoutTryingToListen() = runTest {
        val controller = TvSearchVoiceController(this, { null }, { error("Must not submit") })
        controller.start()
        assertEquals(TvVoicePhase.Error, controller.state.value?.phase)
        assertFalse(controller.state.value!!.retryable)
    }

    @Test fun emptyRecognitionIsRetryableAndDoesNotClearTheExistingQuery() = runTest {
        val f = Fixture(this)
        f.controller.start()
        f.latest.listener.result("  ")
        assertTrue(f.queries.isEmpty())
        assertEquals(TvVoicePhase.Error, f.controller.state.value?.phase)
        assertTrue(f.controller.state.value!!.retryable)
    }

    @Test fun finalResultsRespectTheSearchLengthLimit() = runTest {
        val f = Fixture(this)
        f.controller.start()
        f.latest.listener.result("x".repeat(250))
        assertEquals(listOf("x".repeat(200)), f.queries)
    }

    @Test fun aHungProviderTimesOutAndReleasesTheMicrophone() = runTest {
        val f = Fixture(this)
        f.controller.start()
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(TvVoicePhase.Error, f.controller.state.value?.phase)
        assertTrue(f.latest.closed)
        f.latest.listener.result("too late")
        assertTrue(f.queries.isEmpty())
    }
}
