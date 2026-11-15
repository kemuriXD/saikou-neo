package ani.saikou.neo.shared.pairing

/** How many characters the TV's pairing code has. */
public const val TV_CODE_LENGTH: Int = 6

/**
 * The TV code as the phone keeps it: ASCII letters and digits only, letters in capitals, at most [TV_CODE_LENGTH].
 * Everything else, such as the spaces and dashes a paste brings along, is dropped.
 */
public fun normalizeTvCode(input: String): String =
    input
        .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        .uppercase()
        .take(TV_CODE_LENGTH)
