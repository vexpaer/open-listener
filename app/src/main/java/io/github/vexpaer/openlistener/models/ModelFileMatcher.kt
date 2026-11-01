package io.github.vexpaer.openlistener.models

import java.io.File

/** A suffix-constrained single wildcard matches upstream ONNX role filenames. */
object ModelFileMatcher {
    fun matches(pattern: String, name: String): Boolean {
        if (!pattern.contains('*')) return name == pattern
        if (pattern.count { it == '*' } != 1) return false
        val prefix = pattern.substringBefore('*')
        val suffix = pattern.substringAfter('*')
        return name.startsWith(prefix) && name.endsWith(suffix) &&
            name.length > prefix.length + suffix.length &&
            !name.contains('/') && !name.contains('\\')
    }

    fun resolve(dir: File, pattern: String): File? =
        dir.listFiles()?.singleOrNull { it.isFile && matches(pattern, it.name) }
}
