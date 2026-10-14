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

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The dispatchers behind the `@DefaultDispatcher` and `@IoDispatcher` bindings of [TestCoroutinesModule].
 *
 * Every consumer holds the same [RedirectableDispatcher], so a test redirects the work of use cases that are already
 * injected. [reset] restores the defaults for graphs that outlive a test.
 */
@Singleton
class TestDispatchers
    @Inject
    constructor() {
        val default = RedirectableDispatcher(UnconfinedTestDispatcher())
        val io = RedirectableDispatcher(Dispatchers.IO)

        fun reset() {
            default.reset()
            io.reset()
        }
    }

/** Forwards every dispatch to its current target, which starts as [initial]. */
class RedirectableDispatcher(
    private val initial: CoroutineDispatcher,
) : CoroutineDispatcher() {
    @Volatile
    private var target = initial

    fun redirectTo(dispatcher: CoroutineDispatcher) {
        target = dispatcher
    }

    fun reset() {
        target = initial
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = target.isDispatchNeeded(context)

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) = target.dispatch(context, block)
}
