package ani.saikou.neo.shared.sources

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val HEADER = "IHDR".encodeToByteArray()
private const val MAX_SIDE = 1024L

/**
 * Whether [file] is a PNG the app will draw as a source's logo (ADR 0028): its header says it's at most 1024 pixels a
 * side. The size is read before anything is decoded, so a small file can't ask for a huge picture.
 */
internal fun isUsableIcon(file: ByteArray): Boolean {
    // The signature, then the first chunk's length and type, which must be the header, then its width and height.
    if (file.size < 24) return false
    if (!file.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return false
    if (!file.copyOfRange(12, 16).contentEquals(HEADER)) return false
    val width = file.unsignedInt(16)
    val height = file.unsignedInt(20)
    return width in 1..MAX_SIDE && height in 1..MAX_SIDE
}

private fun ByteArray.unsignedInt(at: Int): Long = (0 until 4).fold(0L) { value, i -> (value shl 8) or (this[at + i].toLong() and 0xFF) }
