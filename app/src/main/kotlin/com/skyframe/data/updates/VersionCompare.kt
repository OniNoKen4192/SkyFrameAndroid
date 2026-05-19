package com.skyframe.data.updates

/**
 * Semver-like version comparison. Pure logic, no dependencies.
 *
 * Algorithm: strip optional leading 'v', split by '.', compare each segment
 * numerically (non-numeric segments treated as 0). Longer versions only win
 * if the extra segments are non-zero — so "0.3.0" and "0.3" are equivalent.
 *
 * Ported from _reference/server/updates/github-release.ts compareVersions.
 */
object VersionCompare {

    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun parse(v: String): List<Int> {
        return v.removePrefix("v")
            .substringBefore('-')  // drop "-beta" / "-rc1" / etc.
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
    }
}
