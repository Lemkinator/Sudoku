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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PreservedFieldsTest : ShouldSpec(
    {
        should("equal another instance with the same bytes, with the same hash code") {
            val fields = PreservedFields(byteArrayOf(0x30, 0x07))
            val same = PreservedFields(byteArrayOf(0x30, 0x07))

            fields shouldBe same
            fields.hashCode() shouldBe same.hashCode()
        }

        should("not equal an instance with other bytes") {
            PreservedFields(byteArrayOf(0x30, 0x07)) shouldNotBe PreservedFields(byteArrayOf(0x30, 0x08))
        }

        should("not equal a value of another type") {
            PreservedFields(byteArrayOf(0x30, 0x07)).equals("3007") shouldBe false
        }

        should("keep no alias of the array it was built from or of the array it returns") {
            val source = byteArrayOf(0x30, 0x07)
            val fields = PreservedFields(source)

            source[1] = 0x08
            fields.bytes[0] = 0x31

            fields.bytes.toList() shouldBe listOf<Byte>(0x30, 0x07)
        }

        should("hold no bytes as NONE") { PreservedFields.NONE.bytes.size shouldBe 0 }

        should("describe itself by its size") { PreservedFields(byteArrayOf(0x30, 0x07)).toString() shouldBe "PreservedFields(2 bytes)" }
    },
)
