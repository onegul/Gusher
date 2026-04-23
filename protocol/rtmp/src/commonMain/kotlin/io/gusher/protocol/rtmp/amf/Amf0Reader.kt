package io.gusher.protocol.rtmp.amf

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readDouble

public object Amf0Reader {
    /**
     * Decodes all top-level AMF0 values from [bytes]. Stops cleanly at EOF.
     */
    public fun decodeAll(bytes: ByteArray): List<Amf0Value> {
        val buffer = Buffer().apply { write(bytes) }
        val out = mutableListOf<Amf0Value>()
        while (!buffer.exhausted()) {
            out += decode(buffer)
        }
        return out
    }

    public fun decode(source: Source): Amf0Value {
        val marker = source.readByte()
        return decodeWithMarker(source, marker)
    }

    private fun decodeWithMarker(source: Source, marker: Byte): Amf0Value = when (marker) {
        Amf0Marker.NUMBER -> Amf0Value.Number(source.readDouble())
        Amf0Marker.BOOLEAN -> Amf0Value.Bool(source.readByte() != 0.toByte())
        Amf0Marker.STRING -> Amf0Value.Str(readShortString(source))
        Amf0Marker.LONG_STRING -> Amf0Value.LongStr(readLongString(source))
        Amf0Marker.OBJECT -> Amf0Value.Object(readObjectBody(source))
        Amf0Marker.ECMA_ARRAY -> {
            // Associative-count is advisory; real streams often lie. Trust the end marker.
            source.readIntBE()
            Amf0Value.EcmaArray(readObjectBody(source))
        }

        Amf0Marker.STRICT_ARRAY -> {
            val count = source.readIntBE()
            require(count >= 0) { "Negative strict-array length: $count" }
            Amf0Value.StrictArray(List(count) { decode(source) })
        }

        Amf0Marker.DATE -> {
            val millis = source.readDouble()
            val tz = source.readShort()
            Amf0Value.Date(millis, tz)
        }

        Amf0Marker.NULL -> Amf0Value.Null
        Amf0Marker.UNDEFINED -> Amf0Value.Undefined
        else -> throw Amf0DecodeException("Unsupported AMF0 marker: 0x${marker.toInt().and(0xFF).toString(16)}")
    }

    private fun readObjectBody(source: Source): Map<String, Amf0Value> {
        val entries = LinkedHashMap<String, Amf0Value>()
        while (true) {
            val nameLen = source.readShort().toInt() and 0xFFFF
            if (nameLen == 0) {
                val end = source.readByte()
                if (end != Amf0Marker.OBJECT_END) {
                    throw Amf0DecodeException("Expected object-end marker, got 0x${end.toInt().and(0xFF).toString(16)}")
                }
                return entries
            }
            val name = source.readByteArray(nameLen).decodeToString()
            entries[name] = decode(source)
        }
    }

    private fun readShortString(source: Source): String {
        val len = source.readShort().toInt() and 0xFFFF
        return source.readByteArray(len).decodeToString()
    }

    private fun readLongString(source: Source): String {
        val len = source.readIntBE()
        require(len >= 0) { "Negative long-string length: $len" }
        return source.readByteArray(len).decodeToString()
    }

    private fun Source.readIntBE(): Int {
        val b0 = readByte().toInt() and 0xFF
        val b1 = readByte().toInt() and 0xFF
        val b2 = readByte().toInt() and 0xFF
        val b3 = readByte().toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}

public class Amf0DecodeException(message: String) : RuntimeException(message)