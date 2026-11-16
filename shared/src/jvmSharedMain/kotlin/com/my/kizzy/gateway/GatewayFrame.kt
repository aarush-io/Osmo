package com.my.kizzy.gateway

/**
 * The few fields of a Discord gateway frame that rich presence uses, read off
 * the frame's bytes without building the rest of it.
 *
 * A user account's `READY` carries the whole account: every server, channel,
 * member list, read state and setting. For an account in many servers that is
 * many megabytes of JSON, and decoding it into a `JsonElement` tree took
 * about ten times its size again. On a phone with a 256 MB heap that tree was
 * the out-of-memory crash seen seconds after rich presence was turned on, and
 * again seconds after each restart, when the socket connected again.
 *
 * Of all that, presence needs four numbers and strings: the opcode, the
 * sequence number, the event name, and from `d` the session id, resume URL
 * and heartbeat interval. [scan] walks the frame once, keeps those, and skips
 * every other value without allocating for it.
 */
data class GatewayFrame(
    val op: Int? = null,
    val s: Int? = null,
    val t: String? = null,
    /** `d.session_id`, from `READY`. */
    val sessionId: String? = null,
    /** `d.resume_gateway_url`, from `READY`. */
    val resumeGatewayUrl: String? = null,
    /** `d.heartbeat_interval`, from `HELLO`. */
    val heartbeatInterval: Long? = null,
) {
    companion object {
        /**
         * Reads [json], a gateway frame as UTF-8 bytes. Returns null for
         * anything that is not a JSON object, or that ends too early.
         *
         * Only the top level and the first level inside `d` are read: a
         * `session_id` deeper down (a `READY` lists the account's other
         * sessions, each with one) is not the session this socket opened.
         */
        fun scan(json: ByteArray): GatewayFrame? =
            runCatching { Scanner(json).frame() }.getOrNull()
    }
}

private class Scanner(private val b: ByteArray) {
    private var i = 0

    private class Malformed : RuntimeException(null, null, false, false)

    fun frame(): GatewayFrame {
        var op: Int? = null
        var s: Int? = null
        var t: String? = null
        var sessionId: String? = null
        var resumeUrl: String? = null
        var heartbeat: Long? = null
        expect('{')
        if (!closesHere('}')) {
            while (true) {
                val key = readString()
                expect(':')
                when (key) {
                    "op" -> op = readNumberOrNull()?.toInt()
                    "s" -> s = readNumberOrNull()?.toInt()
                    "t" -> t = readStringOrNull()
                    "d" -> {
                        skipSpace()
                        if (peek() == '{'.code) {
                            i++
                            if (!closesHere('}')) {
                                while (true) {
                                    val inner = readString()
                                    expect(':')
                                    when (inner) {
                                        "session_id" -> sessionId = readStringOrNull()
                                        "resume_gateway_url" -> resumeUrl = readStringOrNull()
                                        "heartbeat_interval" -> heartbeat = readNumberOrNull()
                                        else -> skipValue()
                                    }
                                    if (!nextMember('}')) break
                                }
                            }
                        } else {
                            skipValue()
                        }
                    }
                    else -> skipValue()
                }
                if (!nextMember('}')) break
            }
        }
        return GatewayFrame(op, s, t, sessionId, resumeUrl, heartbeat)
    }

    private fun peek(): Int = if (i < b.size) b[i].toInt() and 0xFF else throw Malformed()

    private fun skipSpace() {
        while (i < b.size) {
            when (b[i].toInt()) {
                ' '.code, '\n'.code, '\r'.code, '\t'.code -> i++
                else -> return
            }
        }
    }

    private fun expect(c: Char) {
        skipSpace()
        if (peek() != c.code) throw Malformed()
        i++
    }

    /** After `{` or `[`: true, and consumed, if the container is empty. */
    private fun closesHere(close: Char): Boolean {
        skipSpace()
        if (peek() == close.code) {
            i++
            return true
        }
        return false
    }

    /** After a member: true for a `,` (another follows), false for the closing [close]. */
    private fun nextMember(close: Char): Boolean {
        skipSpace()
        return when (peek()) {
            ','.code -> { i++; true }
            close.code -> { i++; false }
            else -> throw Malformed()
        }
    }

    private fun readStringOrNull(): String? {
        skipSpace()
        return if (peek() == 'n'.code) { skipLiteral(); null } else readString()
    }

    private fun readNumberOrNull(): Long? {
        skipSpace()
        if (peek() == 'n'.code) { skipLiteral(); return null }
        val start = i
        while (i < b.size && b[i].toInt().let { it == '-'.code || it == '+'.code || it == '.'.code ||
                it == 'e'.code || it == 'E'.code || it in '0'.code..'9'.code }
        ) i++
        if (i == start) throw Malformed()
        val text = String(b, start, i - start, Charsets.US_ASCII)
        return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong() ?: throw Malformed()
    }

    private fun readString(): String {
        skipSpace()
        if (peek() != '"'.code) throw Malformed()
        i++
        val start = i
        // The common case, no escapes: one copy straight out of the bytes.
        while (true) {
            val c = peek()
            if (c == '"'.code) {
                val s = String(b, start, i - start, Charsets.UTF_8)
                i++
                return s
            }
            if (c == '\\'.code) break
            i++
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(b, start, i - start)
        while (true) {
            val c = peek()
            i++
            when (c) {
                '"'.code -> return out.toString(Charsets.UTF_8.name())
                '\\'.code -> {
                    val e = peek()
                    i++
                    when (e) {
                        'n'.code -> out.write('\n'.code)
                        't'.code -> out.write('\t'.code)
                        'r'.code -> out.write('\r'.code)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        'u'.code -> {
                            if (i + 4 > b.size) throw Malformed()
                            val code = String(b, i, 4, Charsets.US_ASCII).toIntOrNull(16) ?: throw Malformed()
                            i += 4
                            out.write(code.toChar().toString().toByteArray(Charsets.UTF_8))
                        }
                        else -> out.write(e)
                    }
                }
                else -> out.write(c)
            }
        }
    }

    /** A literal: `null`, `true` or `false`. */
    private fun skipLiteral() {
        while (i < b.size && b[i].toInt().let { it in 'a'.code..'z'.code }) i++
    }

    /** Skips one value of any kind, nested ones included, without keeping any of it. */
    private fun skipValue() {
        skipSpace()
        when (peek()) {
            '"'.code -> skipString()
            '{'.code, '['.code -> {
                // Iterative, so a deeply nested value can't overflow the stack.
                var depth = 0
                while (true) {
                    when (peek()) {
                        '"'.code -> { skipString(); continue }
                        '{'.code, '['.code -> depth++
                        '}'.code, ']'.code -> {
                            depth--
                            if (depth == 0) { i++; return }
                        }
                    }
                    i++
                }
            }
            't'.code, 'f'.code, 'n'.code -> skipLiteral()
            else -> readNumberOrNull()
        }
    }

    private fun skipString() {
        i++ // the opening quote
        while (true) {
            when (peek()) {
                '\\'.code -> i += 2
                '"'.code -> { i++; return }
                else -> i++
            }
        }
    }
}
