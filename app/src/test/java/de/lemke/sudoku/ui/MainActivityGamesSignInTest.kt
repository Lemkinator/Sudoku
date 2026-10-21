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

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.di.GamesSignInModule
import de.lemke.sudoku.ui.utils.FakeGamesSignIn
import de.lemke.sudoku.ui.utils.FakePlayGamesIntentProvider
import de.lemke.sudoku.ui.utils.FakePlayGamesSyncApplier
import de.lemke.sudoku.ui.utils.GamesSignIn
import de.lemke.sudoku.ui.utils.PlayGamesIntentProvider
import de.lemke.sudoku.ui.utils.PlayGamesScreen
import de.lemke.sudoku.ui.utils.PlayGamesSyncApplier
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(GamesSignInModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityGamesSignInTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val fakeGamesSignIn = FakeGamesSignIn()

    @BindValue
    @JvmField
    val gamesSignIn: GamesSignIn = fakeGamesSignIn

    private val fakeIntentProvider = FakePlayGamesIntentProvider()

    @BindValue
    @JvmField
    val playGamesIntentProvider: PlayGamesIntentProvider = fakeIntentProvider

    @BindValue
    @JvmField
    val playGamesSyncApplier: PlayGamesSyncApplier = FakePlayGamesSyncApplier()

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
            idleMainLooper()
            scenario.onActivity { activity -> block(activity) }
        }
    }

    private fun clickAchievements(activity: MainActivity) {
        val item =
            activity.binding.navigationView.findMenuItem(R.id.achievements_dest) as androidx.appcompat.view.menu.MenuItemImpl
        item.invoke()
    }

    private fun clickLeaderboards(activity: MainActivity) {
        val item =
            activity.binding.navigationView.findMenuItem(R.id.leaderboards_dest) as androidx.appcompat.view.menu.MenuItemImpl
        item.invoke()
    }

    @Test
    fun `achievements_dest opens achievements directly when already authenticated`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(true)
            clickAchievements(activity)
            idleMainLooper()
            fakeGamesSignIn.authenticationChecks shouldBe 1
            fakeGamesSignIn.signIns shouldBe 0
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.ACHIEVEMENTS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }

    @Test
    fun `achievements_dest signs in then opens achievements when not yet authenticated and sign-in succeeds`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forResult(true)
            clickAchievements(activity)
            idleMainLooper()
            fakeGamesSignIn.signIns shouldBe 1
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.ACHIEVEMENTS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }

    @Test
    fun `achievements_dest shows an error toast when sign-in fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forResult(false)
            clickAchievements(activity)
            idleMainLooper()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `achievements_dest shows an error toast when the sign-in task itself fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forException(RuntimeException("no network"))
            clickAchievements(activity)
            idleMainLooper()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `achievements_dest signs in when the isAuthenticated check itself fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forException(RuntimeException("no network"))
            fakeGamesSignIn.signInResult = Tasks.forResult(true)
            clickAchievements(activity)
            idleMainLooper()
            fakeGamesSignIn.signIns shouldBe 1
        }

    @Test
    fun `an isAuthenticated check that hangs for 10 s ends silently and admits the next tap`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = TaskCompletionSource<Boolean>().task
            clickAchievements(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            fakeGamesSignIn.signIns shouldBe 0
            ShadowToast.shownToastCount() shouldBe 0
            clickAchievements(activity)
            fakeGamesSignIn.authenticationChecks shouldBe 2
        }

    @Test
    fun `tapping achievements_dest twice signs in once`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forResult(false)
            clickAchievements(activity)
            clickAchievements(activity)
            idleMainLooper()
            fakeGamesSignIn.authenticationChecks shouldBe 1
            fakeGamesSignIn.signIns shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
        }

    @Test
    fun `leaderboards_dest opens leaderboards directly when already authenticated`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(true)
            clickLeaderboards(activity)
            idleMainLooper()
            fakeGamesSignIn.authenticationChecks shouldBe 1
            fakeGamesSignIn.signIns shouldBe 0
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.LEADERBOARDS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }

    @Test
    fun `leaderboards_dest signs in then opens leaderboards when not yet authenticated and sign-in succeeds`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forResult(true)
            clickLeaderboards(activity)
            idleMainLooper()
            fakeGamesSignIn.signIns shouldBe 1
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.LEADERBOARDS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }

    @Test
    fun `leaderboards_dest shows an error toast when sign-in fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forResult(false)
            clickLeaderboards(activity)
            idleMainLooper()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `leaderboards_dest shows an error toast when the sign-in task itself fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(false)
            fakeGamesSignIn.signInResult = Tasks.forException(RuntimeException("no network"))
            clickLeaderboards(activity)
            idleMainLooper()
            ShadowToast.getTextOfLatestToast() shouldContain activity.getString(R.string.error_sign_in_failed)
        }

    @Test
    fun `leaderboards_dest signs in when the isAuthenticated check itself fails`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forException(RuntimeException("no network"))
            fakeGamesSignIn.signInResult = Tasks.forResult(true)
            clickLeaderboards(activity)
            idleMainLooper()
            fakeGamesSignIn.signIns shouldBe 1
        }

    @Test
    fun `a failed achievements intent fetch ends silently and admits the next tap`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(true)
            fakeIntentProvider.results += Tasks.forException<Intent>(RuntimeException("no network"))
            clickAchievements(activity)
            idleMainLooper()
            shadowOf(activity).peekNextStartedActivityForResult().shouldBeNull()
            ShadowToast.getLatestToast().shouldBeNull()
            clickAchievements(activity)
            idleMainLooper()
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.ACHIEVEMENTS, PlayGamesScreen.ACHIEVEMENTS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }

    @Test
    fun `a leaderboards intent fetch that hangs for 10 s ends silently and admits the next tap`() =
        launch { activity ->
            fakeGamesSignIn.authenticationCheck = Tasks.forResult(true)
            fakeIntentProvider.results += TaskCompletionSource<Intent>().task
            clickLeaderboards(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            shadowOf(activity).peekNextStartedActivityForResult().shouldBeNull()
            ShadowToast.shownToastCount() shouldBe 0
            clickLeaderboards(activity)
            idleMainLooper()
            fakeIntentProvider.requests shouldBe listOf(PlayGamesScreen.LEADERBOARDS, PlayGamesScreen.LEADERBOARDS)
            shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe "de.lemke.sudoku.test.PLAY_GAMES_SCREEN"
        }
}
