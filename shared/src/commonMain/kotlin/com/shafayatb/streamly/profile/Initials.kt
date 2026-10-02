package com.shafayatb.streamly.profile

/** The first letters of the first and last names, e.g. "Anika Rahman" → "AR". */
internal fun initialsOf(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    val letters = when (words.size) {
        0 -> emptyList()
        1 -> listOf(words.first())
        else -> listOf(words.first(), words.last())
    }
    return letters.joinToString("") { it.take(1) }.uppercase()
}
