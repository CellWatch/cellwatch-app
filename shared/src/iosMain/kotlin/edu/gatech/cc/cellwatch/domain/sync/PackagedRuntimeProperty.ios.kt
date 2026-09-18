package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * Reads a key from the packaged `cellwatch.runtime.properties` bundle resource.
 *
 * Lets iosMain resolve its own configuration rather than having every value
 * threaded in from Swift. That matters here because Kotlin default parameter
 * values are not exported to Swift, so adding a parameter to a harness entry
 * point would break existing Swift call sites.
 *
 * This is the same resource scripts/generate-ios-runtime-properties.sh writes
 * and RuntimeConfigSource reads, so both sides see identical values.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun readPackagedRuntimeProperty(key: String): String? {
    val path = NSBundle.mainBundle.pathForResource("cellwatch.runtime", ofType = "properties")
        ?: return null
    val contents = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        ?: return null
    for (rawLine in contents.split("\n")) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val separator = line.indexOf('=')
        if (separator <= 0) continue
        if (line.substring(0, separator).trim() != key) continue
        return line.substring(separator + 1).trim().trim('"').takeIf { it.isNotEmpty() }
    }
    return null
}
