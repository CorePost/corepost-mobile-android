package com.example.corepostemergencybutton.data

data class CorePostConfig(
    val baseUrl: String = "",
    val emergencyId: String = "",
    val panicSecret: String = "",
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val isComplete: Boolean
        get() = normalizedBaseUrl.isNotBlank() && emergencyId.isNotBlank() && panicSecret.isNotBlank()
}

fun String.maskMiddle(prefix: Int = 4, suffix: Int = 4): String {
    if (length <= prefix + suffix) {
        return this
    }
    return buildString {
        append(take(prefix))
        append("…")
        append(takeLast(suffix))
    }
}
