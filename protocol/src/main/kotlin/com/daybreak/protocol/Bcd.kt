package com.daybreak.protocol

/** Binary-coded decimal: tens digit in the high nibble, ones in the low nibble. */
internal fun toBcd(value: Int): Byte = (((value / 10) shl 4) or (value % 10)).toByte()

internal fun fromBcd(b: Byte): Int {
    val v = ub(b)
    return (v shr 4) * 10 + (v and 0x0F)
}
