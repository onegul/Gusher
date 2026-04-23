package io.gusher.protocol.rtmp.amf

/**
 * AMF0 value types used by RTMP command and data messages.
 *
 * Scope: Gusher implements the subset actually observed on the wire from real CDNs (YouTube, Twitch, Cloudflare,
 * nginx-rtmp). Deprecated markers (Reference, MovieClip, XML Document, Typed Object, AVMPlus) are not supported for
 * encoding and are rejected during decoding.
 */
public sealed interface Amf0Value {
    public data class Number(val value: Double) : Amf0Value
    public data class Bool(val value: Boolean) : Amf0Value
    public data class Str(val value: String) : Amf0Value
    public data class Object(val entries: Map<String, Amf0Value>) : Amf0Value
    public data object Null : Amf0Value
    public data object Undefined : Amf0Value
    public data class EcmaArray(val entries: Map<String, Amf0Value>) : Amf0Value
    public data class StrictArray(val items: List<Amf0Value>) : Amf0Value
    public data class Date(val millisSinceEpoch: Double, val timezone: Short = 0) : Amf0Value
    public data class LongStr(val value: String) : Amf0Value
}