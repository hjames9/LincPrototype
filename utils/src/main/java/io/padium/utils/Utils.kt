package io.padium.utils

import java.util.Collections
import kotlin.collections.MutableMap
import kotlin.collections.LinkedHashMap

object Utils {
    @JvmStatic
    fun <K, V> lruCache(maxSize: Int = 2000): MutableMap<K, V> {
        val cache = object : LinkedHashMap<K, V>(maxSize * 4 / 3, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
                return size > maxSize
            }
        }
        return Collections.synchronizedMap(cache)
    }
}
