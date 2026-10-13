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

package de.lemke.sudoku

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.InstallIn
import dagger.hilt.android.EarlyEntryPoint
import dagger.hilt.android.EarlyEntryPoints
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.HiltTestRule
import de.lemke.sudoku.data.database.AppDatabase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@EarlyEntryPoint
@InstallIn(SingletonComponent::class)
interface HiltTestRuleTestEntryPoint {
    fun database(): AppDatabase
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class HiltTestRuleTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Test
    fun `closes the injected database after the test`() {
        HiltTestRule.closeAfterTest(AutoCloseable { database.isOpen shouldBe false })
        hiltRule.inject()
        database.openHelper.writableDatabase
        database.isOpen shouldBe true
    }

    @Test
    fun `builds no database to close when the test never injects`() {
        mockkObject(TestPersistenceModule)
        HiltTestRule.closeAfterTest(
            AutoCloseable {
                try {
                    verify(exactly = 0) { TestPersistenceModule.provideTestAppDatabase(any()) }
                } finally {
                    unmockkObject(TestPersistenceModule)
                }
            },
        )
    }

    class OutcomeRule : TestRule {
        var verify: (Throwable?) -> Unit = { failure -> failure?.let { throw it } }

        override fun apply(
            base: Statement,
            description: Description,
        ) = object : Statement() {
            override fun evaluate() {
                val failure = runCatching { base.evaluate() }.exceptionOrNull()
                verify(failure)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class HiltTestRuleEarlyEntryPointTest {
    @get:Rule(order = 0)
    val outcome = HiltTestRuleTest.OutcomeRule()

    @get:Rule(order = 1)
    val closeTestResources = HiltTestRule.closeResourcesRule()

    @Test
    fun `closes the early entry point database after the test`() {
        val database =
            EarlyEntryPoints
                .get(ApplicationProvider.getApplicationContext<HiltTestApplication>(), HiltTestRuleTestEntryPoint::class.java)
                .database()
        database.openHelper.writableDatabase
        database.isOpen shouldBe true
        outcome.verify = { failure ->
            failure.shouldBeNull()
            database.isOpen shouldBe false
        }
    }
}
