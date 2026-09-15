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

/**
 * `FileProvider.getUriForFile` caches its resolved `PathStrategy` per authority in a private
 * static `sCache`, keyed only by authority string. Robolectric gives every `@Test` method a fresh
 * `Application` (and therefore a fresh, differently-pathed cache/files dir), but that static cache
 * outlives the JVM fork across test methods and classes. Once one test resolves and caches a
 * strategy for this app's authority, a later test's file - living under a *different* Application's
 * cache dir - no longer starts with the cached root path, so `getUriForFile` throws
 * `IllegalArgumentException("Failed to find configured root that contains ...")` or the call site
 * built on top of it observes a missing/empty result.
 *
 * `FileProvider.attachInfo` clears this cache for its own authority, but only once Robolectric
 * actually instantiates and attaches that provider - which doesn't reliably happen before the
 * first `getUriForFile` call in a plain unit test. Call this from `@Before` on any Robolectric test
 * that calls `FileProvider.getUriForFile` for real (i.e. not `mockkStatic`'d) so each test starts
 * with no inherited entry.
 */
fun resetFileProviderCache() {
    // Reflects by name rather than importing androidx.core.content.FileProvider - the testFixtures
    // source set has no compile-time dependency on androidx.core, only the app's main source set does.
    val sCache = Class.forName("androidx.core.content.FileProvider").getDeclaredField("sCache")
    sCache.isAccessible = true
    (sCache.get(null) as MutableMap<*, *>).clear()
}
