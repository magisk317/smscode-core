package io.github.magisk317.smscode.verification

/** Resolves the text sent to the input surface without changing the canonical SMS code. */
object AutoInputCodeResolver {
    private const val ONE = 1
    private const val TWO = 2
    private const val THREE = 3
    private const val FOUR = 4
    private const val FIVE = 5
    private const val SIX = 6
    private const val SEVEN = 7
    private const val EIGHT = 8
    private const val NINE = 9
    private const val TEN = 10

    private val instructionPattern = Regex(
        "(?:输入|填写)[^。！？\\n]{0,20}?(前|后)([0-9一二两三四五六七八九十]+)位",
    )

    fun resolve(fullCode: String?, body: String?): String? {
        val code = fullCode?.takeIf { it.isNotBlank() } ?: return fullCode
        val instruction = instructionPattern.find(body.orEmpty()) ?: return code
        val count = parseCount(instruction.groupValues[2]) ?: return code
        if (count <= 0 || count > code.length) return code

        return if (instruction.groupValues[1] == "前") {
            code.take(count)
        } else {
            code.takeLast(count)
        }
    }

    private fun parseCount(token: String): Int? {
        return token.toIntOrNull() ?: when (token) {
            "一" -> ONE
            "二", "两" -> TWO
            "三" -> THREE
            "四" -> FOUR
            "五" -> FIVE
            "六" -> SIX
            "七" -> SEVEN
            "八" -> EIGHT
            "九" -> NINE
            "十" -> TEN
            else -> null
        }
    }
}
