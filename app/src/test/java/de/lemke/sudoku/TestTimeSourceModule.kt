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

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.lemke.sudoku.di.TimeSourceModule
import javax.inject.Singleton
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/** Reads Robolectric's looper time, which only advances when a test idles the looper. */
object LooperTimeSource : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    override fun read(): Long = SystemClock.uptimeMillis()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TimeSourceModule::class])
object TestTimeSourceModule {
    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource.WithComparableMarks = LooperTimeSource
}
