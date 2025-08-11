package com.app.mdlbapp.reward

/** Новое значение баланса после дельты, но ниже нуля не уходим. */
fun clampAfterDelta(current: Long, delta: Int): Long {
    val raw = current + delta
    return if (raw < 0L) 0L else raw
}