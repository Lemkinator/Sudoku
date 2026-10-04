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

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * Runs blocks in place like [kotlinx.coroutines.Dispatchers.Unconfined], except that blocks dispatched between [pause]
 * and [resume] wait, and [resume] hands them to [delegate].
 */
internal class PausableDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    private val held = ArrayDeque<Pair<CoroutineContext, Runnable>>()
    private var paused = false

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = paused

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        if (paused) held.addLast(context to block) else delegate.dispatch(context, block)
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        while (held.isNotEmpty()) {
            val (context, block) = held.removeFirst()
            delegate.dispatch(context, block)
        }
    }
}
