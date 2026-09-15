/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.ui

import android.app.Activity
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.R
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.di.GamesSignInModule
import de.lemke.sudoku.ui.utils.GamesSignInProvider
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private val AUTHENTICATED: AuthenticationResult = listOf(AuthenticationResult.zza, AuthenticationResult.zzb).first { it.isAuthenticated }
private val NOT_AUTHENTICATED: AuthenticationResult =
    listOf(AuthenticationResult.zza, AuthenticationResult.zzb).first { !it.isAuthenticated }

/**
 * Covers [MainActivity]'s `achievements_dest`/`leaderboards_dest` navigation and `signInPlayGames`, faking the
 * [GamesSignInClient] boundary through [GamesSignInProvider] — Robolectric has no real, connected Play Games
 * session, so `isAuthenticated()`/`signIn()` can't be driven through the real SDK the way [MainActivityMenuTest]
 * drives the other drawer items.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class, GamesSignInModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityGamesSignInTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    private val fakeClient: GamesSignInClient = mockk()

    @BindValue
    @JvmField
    val fakeGamesSignInProvider: GamesSignInProvider =
        object : GamesSignInProvider {
            override fun getClient(activity: Activity): GamesSignInClient = fakeClient
        }

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun launch(block: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> block(activity) }
        }
    }

    private fun clickAchievements(activity: MainActivity) {
        android.os.SystemClock.sleep(601L)
        val item =
            activity.binding.navigationView.findMenuItem(R.id.achievements_dest) as androidx.appcompat.view.menu.MenuItemImpl
        item.invoke()
    }

    private fun clickLeaderboards(activity: MainActivity) {
        android.os.SystemClock.sleep(601L)
        val item =
            activity.binding.navigationView.findMenuItem(R.id.leaderboards_dest) as androidx.appcompat.view.menu.MenuItemImpl
        item.invoke()
    }

    @Test
    fun `achievements_dest opens achievements directly when already authenticated`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(AUTHENTICATED)
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.isAuthenticated() }
            verify(exactly = 0) { fakeClient.signIn() }
        }

    @Test
    fun `achievements_dest signs in then opens achievements when not yet authenticated and sign-in succeeds`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forResult(AUTHENTICATED)
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.signIn() }
        }

    @Test
    fun `achievements_dest shows an error toast when sign-in fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forResult(NOT_AUTHENTICATED)
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `achievements_dest shows an error toast when the sign-in task itself fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forException(RuntimeException("no network"))
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `achievements_dest signs in when the isAuthenticated check itself fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forException(RuntimeException("no network"))
            every { fakeClient.signIn() } returns Tasks.forResult(AUTHENTICATED)
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.signIn() }
        }

    @Test
    fun `leaderboards_dest opens leaderboards directly when already authenticated`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(AUTHENTICATED)
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.isAuthenticated() }
            verify(exactly = 0) { fakeClient.signIn() }
        }

    @Test
    fun `leaderboards_dest signs in then opens leaderboards when not yet authenticated and sign-in succeeds`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forResult(AUTHENTICATED)
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.signIn() }
        }

    @Test
    fun `leaderboards_dest shows an error toast when sign-in fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forResult(NOT_AUTHENTICATED)
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `leaderboards_dest shows an error toast when the sign-in task itself fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forResult(NOT_AUTHENTICATED)
            every { fakeClient.signIn() } returns Tasks.forException(RuntimeException("no network"))
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `leaderboards_dest signs in when the isAuthenticated check itself fails`() =
        launch { activity ->
            every { fakeClient.isAuthenticated() } returns Tasks.forException(RuntimeException("no network"))
            every { fakeClient.signIn() } returns Tasks.forResult(AUTHENTICATED)
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { fakeClient.signIn() }
        }
}
