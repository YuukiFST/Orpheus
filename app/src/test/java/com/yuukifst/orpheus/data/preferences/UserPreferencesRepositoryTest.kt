package com.yuukifst.orpheus.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UserPreferencesRepositoryTest {

    private fun createTestContext(): Context {
        val context = mockk<Context>(relaxed = true)
        every {
            context.getSharedPreferences("orpheus_startup_mirror", Context.MODE_PRIVATE)
        } returns mockk(relaxed = true)
        return context
    }

    private fun createRepository(
        backgroundScope: kotlinx.coroutines.CoroutineScope,
        tempDir: java.nio.file.Path
    ): UserPreferencesRepository {
        return UserPreferencesRepository(
            context = createTestContext(),
            dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            ),
            json = Json
        )
    }

    @Test
    fun `clearPreferencesExceptKeys preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.clearPreferencesExceptKeys(emptySet())

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals(NavBarStyle.DEFAULT, repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup clearExisting preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_style",
                        type = "string",
                        stringValue = "restored"
                    )
                ),
                clearExisting = true
            )

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals("restored", repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navBarCornerRadiusFlow clamps values outside the supported UI range`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)

            repository.setNavBarCornerRadius(-1)
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.setNavBarCornerRadius(999)
            assertEquals(MAX_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_corner_radius",
                        type = "int",
                        intValue = -1
                    )
                ),
                clearExisting = false
            )
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `useSmoothCornersFlow defaults to true when unset`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)
            assertTrue(repository.useSmoothCornersFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navBarCornerRadiusFlow defaults to 28 when unset`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)
            assertEquals(DEFAULT_NAV_BAR_CORNER_RADIUS_DP, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setUseSmoothCorners true promotes zero radius to default`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)
            repository.setNavBarCornerRadius(0)
            repository.setUseSmoothCorners(true)
            assertEquals(DEFAULT_NAV_BAR_CORNER_RADIUS_DP, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setUseSmoothCorners false leaves zero radius`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = createRepository(backgroundScope, tempDir)
            repository.setUseSmoothCorners(true)
            repository.setNavBarCornerRadius(0)
            repository.setUseSmoothCorners(false)
            assertFalse(repository.useSmoothCornersFlow.first())
            assertEquals(0, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `promoteZeroNavBarRadiusIfRounded upgrades legacy rounded zero radius`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            // Simulate legacy: Rounded ON with explicit radius 0 via public APIs.
            val repository = UserPreferencesRepository(
                context = createTestContext(),
                dataStore = dataStore,
                json = Json,
            )
            repository.setUseSmoothCorners(false)
            repository.setNavBarCornerRadius(0)
            // Force rounded + zero without promotion: write via edit after square path.
            dataStore.edit { prefs ->
                prefs[androidx.datastore.preferences.core.booleanPreferencesKey("use_smooth_corners")] = true
                prefs[androidx.datastore.preferences.core.intPreferencesKey("nav_bar_corner_radius")] = 0
            }
            repository.promoteZeroNavBarRadiusIfRounded()
            assertEquals(DEFAULT_NAV_BAR_CORNER_RADIUS_DP, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
