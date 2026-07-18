package io.github.magisk317.smscode.runtime.common.prefs

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferenceCommitCoordinatorTest {
    private val enabled = PreferenceSpec.boolean("enabled", defaultValue = false)

    @Test
    fun `persistence receives one batch before ordered hooks`() = runBlocking {
        val events = mutableListOf<String>()
        var persistedChanges: PreferenceChangeSet? = null
        val coordinator = PreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence { changes ->
                events += "persist"
                persistedChanges = changes
                true
            },
            hooks = listOf(
                PreferencePostCommitHooks.invalidate { events += "invalidate" },
                PreferencePostCommitHooks.publish { commit ->
                    events += "publish:${commit.changes.changedKeys.single()}"
                },
            ),
        )

        val result = coordinator.commit { set(enabled, true) }

        assertEquals(listOf("persist", "invalidate", "publish:enabled"), events)
        assertSame(persistedChanges, result.changes)
        assertTrue((result as PreferenceCommitResult.Persisted).postCommitSucceeded)
    }

    @Test
    fun `rejected persistence skips every hook`() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = PreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence {
                events += "persist"
                false
            },
            hooks = listOf(
                PreferencePostCommitHooks.invalidate { events += "invalidate" },
                PreferencePostCommitHooks.publish { events += "publish" },
            ),
        )

        val result = coordinator.commit { set(enabled, true) }

        assertEquals(listOf("persist"), events)
        assertTrue(result is PreferenceCommitResult.NotPersisted)
        assertNull((result as PreferenceCommitResult.NotPersisted).error)
    }

    @Test
    fun `persistence exception is returned and skips hooks`() = runBlocking {
        val failure = IllegalStateException("disk unavailable")
        var hookCalled = false
        val coordinator = PreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence { throw failure },
            hooks = listOf(
                PreferencePostCommitHooks.publish { hookCalled = true },
            ),
        )

        val result = coordinator.commit { set(enabled, true) }

        assertFalse(hookCalled)
        assertSame(failure, (result as PreferenceCommitResult.NotPersisted).error)
    }

    @Test
    fun `hook failure is reported without hiding persistence or later hooks`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalArgumentException("invalidate failed")
        val coordinator = PreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence {
                events += "persist"
                true
            },
            hooks = listOf(
                PreferencePostCommitHooks.invalidate {
                    events += "invalidate"
                    throw failure
                },
                PreferencePostCommitHooks.publish { events += "publish" },
            ),
        )

        val result = coordinator.commit { set(enabled, true) } as PreferenceCommitResult.Persisted

        assertEquals(listOf("persist", "invalidate", "publish"), events)
        assertFalse(result.postCommitSucceeded)
        assertEquals("invalidate", result.postCommitFailures.single().hookName)
        assertSame(failure, result.postCommitFailures.single().error)
    }

    @Test
    fun `empty edit skips persistence and hooks`() = runBlocking {
        var persistenceCalled = false
        var hookCalled = false
        val coordinator = PreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence {
                persistenceCalled = true
                true
            },
            hooks = listOf(
                PreferencePostCommitHooks.publish { hookCalled = true },
            ),
        )

        val result = coordinator.commit { }

        assertTrue(result is PreferenceCommitResult.NoChanges)
        assertFalse(persistenceCalled)
        assertFalse(hookCalled)
    }
}
