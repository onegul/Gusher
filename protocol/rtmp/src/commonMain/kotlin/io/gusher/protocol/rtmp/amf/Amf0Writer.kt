package io.gusher.protocol.rtmp.amf

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import kotlinx.io.writeDouble

public object Amf0Writer {
    /**
     * Encodes a sequence of AMF0 values into a freshly-allocated byte array.
     * RTMP command messages concatenate multiple top-level values without delimiters.
     */
    public fun encodeAll(values: List<Amf0Value>): ByteArray {
        val buffer = Buffer()
        values.forEach { encode(buffer, it) }
        return buffer.readByteArray()
    }

    public fun encode(sink: Sink, value: Amf0Value) {
        when (value) {
            is Amf0Value.Number -> {
                sink.writeByte(Amf0Marker.NUMBER)
                sink.writeDouble(value.value)
            }

            is Amf0Value.Bool -> {
                sink.writeByte(Amf0Marker.BOOLEAN)
                sink.writeByte(if (value.value) 1 else 0)
            }

            is Amf0Value.Str -> {
                val bytes = value.value.encodeToByteArray()
                if (bytes.size > 0xFFFF) {
                    // Promote to long string automatically.
                    encode(sink, Amf0Value.LongStr(value.value))
                } else {
                    sink.writeByte(Amf0Marker.STRING)
                    sink.writeShort(bytes.size.toShort())
                    sink.write(bytes)
                }
            }

            is Amf0Value.LongStr -> {
                val bytes = value.value.encodeToByteArray()
                sink.writeByte(Amf0Marker.LONG_STRING)
                sink.writeIntBE(bytes.size)
                sink.write(bytes)
            }

            is Amf0Value.Object -> {
                sink.writeByte(Amf0Marker.OBJECT)
                writeObjectBody(sink, value.entries)
            }

            is Amf0Value.EcmaArray -> {
                sink.writeByte(Amf0Marker.ECMA_ARRAY)
                sink.writeIntBE(value.entries.size)
                writeObjectBody(sink, value.entries)
            }

            is Amf0Value.StrictArray -> {
                sink.writeByte(Amf0Marker.STRICT_ARRAY)
                sink.writeIntBE(value.items.size)
                value.items.forEach { encode(sink, it) }
            }

            is Amf0Value.Date -> {
                sink.writeByte(Amf0Marker.DATE)
                sink.writeDouble(value.millisSinceEpoch)
                sink.writeShort(value.timezone)
            }

            Amf0Value.Null -> sink.writeByte(Amf0Marker.NULL)
            Amf0Value.Undefined -> sink.writeByte(Amf0Marker.UNDEFINED)
        }
    }

    private fun writeObjectBody(sink: Sink, entries: Map<String, Amf0Value>) {
        entries.forEach { (key, v) ->
            val keyBytes = key.encodeToByteArray()
            require(keyBytes.size <= 0xFFFF) { "AMF0 object key too long: ${keyBytes.size}" }
            sink.writeShort(keyBytes.size.toShort())
            sink.write(keyBytes)
            encode(sink, v)
        }
        // Object end marker: empty name + end marker byte
        sink.writeShort(0)
        sink.writeByte(Amf0Marker.OBJECT_END)
    }

    private fun Sink.writeIntBE(value: Int) {
        writeByte((value ushr 24).toByte())
        writeByte((value ushr 16).toByte())
        writeByte((value ushr 8).toByte())
        writeByte(value.toByte())
    }
}