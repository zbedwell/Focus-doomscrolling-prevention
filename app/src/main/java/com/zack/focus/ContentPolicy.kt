package com.zack.focus

object ContentPolicy {

    private val blockedKeywords = setOf(
        "porn", "xxx", "nsfw", "onlyfans",
        "casino", "gambling", "betting", "slots",
        "gore", "graphic violence",
        "cocaine", "heroin", "meth"
    )

    fun containsBlockedKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return blockedKeywords.any { lower.contains(it) }
    }
}
