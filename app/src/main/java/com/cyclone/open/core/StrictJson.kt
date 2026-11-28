/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/forge/StrictJson.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Adds bounded UTF-8 reads and uses anchored number parsing.
 */
package com.cyclone.open.core

import org.json.JSONTokener
import java.io.InputStream
import java.nio.ByteBuffer

internal object StrictJson {
    private val literal = Regex("(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)")

    fun readBytes(input: InputStream, maxBytes: Int): ByteArray = input.readNBytes(maxBytes + 1).also {
        require(it.size <= maxBytes) { "JSON input is too large." }
    }

    fun text(bytes: ByteArray): String = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()

    fun check(text: String) = Parser(text).check()

    private class Parser(private val text: String) {
        private var index = 0
        private fun space() {
            while (index < text.length && text[index] in " \t\r\n") index++
        }

        private fun take(char: Char): Boolean {
            space()
            if (index == text.length || text[index] != char) return false
            index++
            return true
        }

        private fun expect(char: Char) = require(take(char)) { "Malformed JSON." }

        fun check() {
            value(0)
            space()
            require(index == text.length) { "Trailing JSON data." }
        }

        private fun string(): String {
            space()
            val start = index
            expect('"')
            while (index < text.length) {
                val next = text[index++]
                require(next >= ' ') { "Control character in JSON string." }
                if (next == '"') return JSONTokener(text.substring(start, index)).nextValue() as String
                if (next == '\\') {
                    require(index < text.length) { "Incomplete JSON escape." }
                    val escaped = text[index++]
                    require(escaped in "\"\\/bfnrtu") { "Invalid JSON escape." }
                    if (escaped == 'u') {
                        require(index + 4 <= text.length && text.substring(index, index + 4).all { it in "0123456789abcdefABCDEF" })
                        index += 4
                    }
                }
            }
            throw IllegalArgumentException("Unterminated JSON string.")
        }

        private fun value(depth: Int) {
            require(depth <= 32) { "JSON is too deeply nested." }
            space()
            require(index < text.length) { "Incomplete JSON." }
            when (text[index]) {
                '{' -> {
                    index++
                    if (take('}')) return
                    val keys = mutableSetOf<String>()
                    do {
                        require(keys.add(string())) { "Duplicate JSON key." }
                        expect(':')
                        value(depth + 1)
                    } while (take(','))
                    expect('}')
                }
                '[' -> {
                    index++
                    if (take(']')) return
                    do { value(depth + 1) } while (take(','))
                    expect(']')
                }
                '"' -> string()
                else -> {
                    val match = requireNotNull(literal.matchAt(text, index)) { "Invalid JSON value." }
                    index += match.value.length
                }
            }
        }
    }
}
