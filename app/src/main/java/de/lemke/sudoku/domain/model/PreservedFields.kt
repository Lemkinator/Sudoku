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

package de.lemke.sudoku.domain.model

/** Encoded fields of a stored board that this version does not know, kept verbatim so a save does not drop them. */
class PreservedFields(bytes: ByteArray) {
    private val stored = bytes.copyOf()

    val bytes: ByteArray get() = stored.copyOf()

    override fun equals(other: Any?): Boolean = other is PreservedFields && stored.contentEquals(other.stored)

    override fun hashCode(): Int = stored.contentHashCode()

    override fun toString(): String = "PreservedFields(${stored.size} bytes)"

    companion object {
        val NONE = PreservedFields(ByteArray(0))
    }
}
