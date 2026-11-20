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

package de.lemke.sudoku.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PersistenceModuleTest {
    @Test
    fun `provides an empty database stored under the name app`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = PersistenceModule.provideAppDatabase(context)
        try {
            runBlocking { PersistenceModule.provideGameDao(database).getAll() }.shouldBeEmpty()
            context.getDatabasePath("app").exists() shouldBe true
        } finally {
            database.close()
        }
    }
}
