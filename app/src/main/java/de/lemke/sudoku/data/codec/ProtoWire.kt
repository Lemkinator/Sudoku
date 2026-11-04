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

import java.io.ByteArrayOutputStream

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5
private const val WIRE_TYPE_MASK = 0x07L
private const val TAG_SHIFT = 3
private const val FIXED64_BYTES = 8
private const val FIXED32_BYTES = 4
private const val MAX_VARINT_BYTES = 10
private const val VARINT_PAYLOAD_BITS = 7
private const val VARINT_PAYLOAD_MASK = 0x7f
private const val VARINT_CONTINUATION = 0x80

/** One field of a protobuf wire message. [raw] holds the field exactly as it was encoded, tag included. */
sealed interface ProtoField {
    val number: Int
    val raw: ByteArray
}

class VarintField(override val number: Int, val value: Long, override val raw: ByteArray) : ProtoField

class BytesField(override val number: Int, val value: ByteArray, override val raw: ByteArray) : ProtoField

/** A fixed32 or fixed64 field, which no codec of this app reads but a reader must skip and keep. */
class FixedField(override val number: Int, override val raw: ByteArray) : ProtoField

/** Writes the protobuf wire format: varint and length-delimited fields. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(
        number: Int,
        value: Long,
    ) {
        tag(number, WIRE_VARINT)
        packedVarint(value)
    }

    fun bytes(
        number: Int,
        value: ByteArray,
    ) {
        tag(number, WIRE_LENGTH_DELIMITED)
        packedVarint(value.size.toLong())
        out.write(value)
    }

    fun string(
        number: Int,
        value: String,
    ) = bytes(number, value.toByteArray(Charsets.UTF_8))

    /** Appends already encoded fields. */
    fun raw(encoded: ByteArray) = out.write(encoded)

    /** Appends a varint without a tag, the element format of a packed repeated field. */
    fun packedVarint(value: Long) {
        var rest = value
        while (rest and VARINT_PAYLOAD_MASK.toLong().inv() != 0L) {
            out.write((rest.toInt() and VARINT_PAYLOAD_MASK) or VARINT_CONTINUATION)
            rest = rest ushr VARINT_PAYLOAD_BITS
        }
        out.write(rest.toInt())
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(
        number: Int,
        wireType: Int,
    ) = packedVarint((number.toLong() shl TAG_SHIFT) or wireType.toLong())
}

/** Reads the protobuf wire format. Every function returns null for a message that is truncated or malformed. */
object ProtoReader {
    /** The fields of [bytes] in order, or null for a group, a reserved wire type, field number 0 or a truncated field. */
    fun fields(bytes: ByteArray): List<ProtoField>? {
        val cursor = Cursor(bytes)
        val fields = mutableListOf<ProtoField>()
        while (cursor.hasMore) {
            val field = cursor.field() ?: return null
            fields += field
        }
        return fields
    }

    /** The varints of a packed repeated field. */
    fun packedVarints(bytes: ByteArray): List<Long>? {
        val cursor = Cursor(bytes)
        val values = mutableListOf<Long>()
        while (cursor.hasMore) {
            val value = cursor.varint() ?: return null
            values += value
        }
        return values
    }

    private class Cursor(private val bytes: ByteArray) {
        private var position = 0

        val hasMore: Boolean get() = position < bytes.size

        fun varint(): Long? {
            var result = 0L
            for (index in 0 until MAX_VARINT_BYTES) {
                val byte = bytes.getOrNull(position++)?.toInt() ?: break
                result = result or ((byte and VARINT_PAYLOAD_MASK).toLong() shl (index * VARINT_PAYLOAD_BITS))
                if (byte and VARINT_CONTINUATION == 0) return result
            }
            return null
        }

        fun field(): ProtoField? {
            val start = position
            val tag = varint()
            val number = tag?.ushr(TAG_SHIFT)?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
            return if (tag == null || number == null) {
                null
            } else {
                when ((tag and WIRE_TYPE_MASK).toInt()) {
                    WIRE_VARINT -> varint()?.let { VarintField(number, it, rawSince(start)) }
                    WIRE_LENGTH_DELIMITED -> take(varint())?.let { BytesField(number, it, rawSince(start)) }
                    WIRE_FIXED64 -> take(FIXED64_BYTES.toLong())?.let { FixedField(number, rawSince(start)) }
                    WIRE_FIXED32 -> take(FIXED32_BYTES.toLong())?.let { FixedField(number, rawSince(start)) }
                    else -> null
                }
            }
        }

        private fun take(count: Long?): ByteArray? =
            count?.takeIf { it in 0..bytes.size - position }?.toInt()?.let { length ->
                bytes.copyOfRange(position, position + length).also { position += length }
            }

        private fun rawSince(start: Int): ByteArray = bytes.copyOfRange(start, position)
    }
}
