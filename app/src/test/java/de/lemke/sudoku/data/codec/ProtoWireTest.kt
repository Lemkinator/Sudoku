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

package de.lemke.sudoku.data.codec

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

@OptIn(ExperimentalStdlibApi::class)
class ProtoWireTest : ShouldSpec(
    {
        should("write a varint field with its tag") {
            ProtoWriter().apply { varint(1, 150) }.toByteArray().toHexString() shouldBe "08" + "9601"
        }

        should("write a field number above 15 with a two byte tag") {
            ProtoWriter().apply { varint(16, 1) }.toByteArray().toHexString() shouldBe "8001" + "01"
        }

        should("write a string as length delimited UTF-8") {
            ProtoWriter().apply { string(2, "aé") }.toByteArray().toHexString() shouldBe "12" + "03" + "61c3a9"
        }

        should("write a negative varint in ten bytes") {
            ProtoWriter().apply { packedVarint(-1) }.toByteArray().toHexString() shouldBe "ffffffffffffffffff01"
        }

        should("append raw bytes unchanged") {
            ProtoWriter().apply { raw("3007".hexToByteArray()) }.toByteArray().toHexString() shouldBe "3007"
        }

        should("read a varint field and keep its raw bytes") {
            val field = ProtoReader.fields("089601".hexToByteArray()).shouldNotBeNull().single()
            field.shouldBeInstanceOf<VarintField>().value shouldBe 150L
            field.number shouldBe 1
            field.raw.toHexString() shouldBe "089601"
        }

        should("read a length delimited field") {
            val field = ProtoReader.fields("120361c3a9".hexToByteArray()).shouldNotBeNull().single()
            field.shouldBeInstanceOf<BytesField>().value.toHexString() shouldBe "61c3a9"
            field.number shouldBe 2
        }

        should("read fixed32 and fixed64 fields as opaque") {
            val fields = ProtoReader.fields("0d01020304".hexToByteArray() + "11".hexToByteArray() + ByteArray(8) { 9 }).shouldNotBeNull()
            fields.map { it.shouldBeInstanceOf<FixedField>().number } shouldBe listOf(1, 2)
            fields[0].raw.toHexString() shouldBe "0d01020304"
        }

        should("read packed varints") {
            ProtoReader.packedVarints("0106ac02".hexToByteArray()) shouldBe listOf(1L, 6L, 300L)
        }

        should("round trip a large varint") {
            val bytes = ProtoWriter().apply { varint(3, Long.MAX_VALUE) }.toByteArray()
            ProtoReader
                .fields(bytes)
                .shouldNotBeNull()
                .single()
                .shouldBeInstanceOf<VarintField>()
                .value shouldBe Long.MAX_VALUE
        }

        should("reject a truncated tag") { ProtoReader.fields("80".hexToByteArray()).shouldBeNull() }

        should("reject a truncated varint value") { ProtoReader.fields("0896".hexToByteArray()).shouldBeNull() }

        should("reject a varint longer than ten bytes") {
            ProtoReader.fields("08".hexToByteArray() + ByteArray(10) { 0x80.toByte() } + byteArrayOf(1)).shouldBeNull()
        }

        should("reject a length beyond the message") { ProtoReader.fields("1205616263".hexToByteArray()).shouldBeNull() }

        should("reject a negative length") {
            ProtoReader.fields("12".hexToByteArray() + ByteArray(9) { 0xff.toByte() } + byteArrayOf(1)).shouldBeNull()
        }

        should("reject a truncated fixed32") { ProtoReader.fields("0d010203".hexToByteArray()).shouldBeNull() }

        should("reject a truncated fixed64") { ProtoReader.fields("11010203".hexToByteArray()).shouldBeNull() }

        should("reject field number 0") { ProtoReader.fields("0001".hexToByteArray()).shouldBeNull() }

        should("reject a field number beyond Int") {
            ProtoReader.fields("ffffffff1f01".hexToByteArray()).shouldBeNull()
        }

        should("reject group wire types") {
            ProtoReader.fields("0b".hexToByteArray()).shouldBeNull()
            ProtoReader.fields("0c".hexToByteArray()).shouldBeNull()
        }

        should("reject reserved wire types") {
            ProtoReader.fields("0e".hexToByteArray()).shouldBeNull()
            ProtoReader.fields("0f".hexToByteArray()).shouldBeNull()
        }

        should("reject a truncated packed varint") { ProtoReader.packedVarints("01ac".hexToByteArray()).shouldBeNull() }
    },
)
