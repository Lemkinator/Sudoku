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

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.lemke.commonutils.di.ApplicationScope
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.di.AppCoroutinesModule
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppCoroutinesModule::class])
object TestCoroutinesModule {
    @Provides
    @DefaultDispatcher
    fun provideDefault(dispatchers: TestDispatchers): CoroutineDispatcher = dispatchers.default

    @Provides
    @IoDispatcher
    fun provideIo(dispatchers: TestDispatchers): CoroutineDispatcher = dispatchers.io

    @Provides
    @MainDispatcher
    fun provideMain(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
}
