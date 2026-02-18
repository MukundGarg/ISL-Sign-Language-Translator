package com.example.signtranslator

class SentenceManager {

    private val buffer = StringBuilder()

    companion object {
        /** Labels whose length > 1 are treated as complete words. */
        private fun isWord(label: String) = label.length > 1
    }

    // ── add letter or word ─────────────────────────────────────────────────
    fun addLetter(label: String) {
        if (isWord(label)) {
            ensureSpaceBefore()
            buffer.append(label)
            buffer.append(' ')
        } else {
            buffer.append(label)
        }
    }

    fun addSpace() {
        if (buffer.isNotEmpty() && !buffer.last().isWhitespace()) {
            buffer.append(' ')
        }
    }

    // ── delete ─────────────────────────────────────────────────────────────
    fun deleteLastCharacter() {
        if (buffer.isEmpty()) return
        if (buffer.last() == ' ') {
            // Remove trailing space AND the preceding word (whole token)
            buffer.deleteCharAt(buffer.lastIndex)
            val lastSpace = buffer.lastIndexOf(' ')
            if (lastSpace == -1) buffer.clear()
            else buffer.delete(lastSpace + 1, buffer.length)
        } else {
            buffer.deleteCharAt(buffer.lastIndex)
        }
    }

    fun clearSentence() {
        buffer.clear()
    }

    // ── query ──────────────────────────────────────────────────────────────
    fun getSentence(): String =
        buffer.toString().trimEnd().replace(Regex("\\s{2,}"), " ")

    // ── internal ──────────────────────────────────────────────────────────
    private fun ensureSpaceBefore() {
        if (buffer.isNotEmpty() && !buffer.last().isWhitespace()) {
            buffer.append(' ')
        }
    }
}
