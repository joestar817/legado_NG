package io.legado.app.help.source

import java.io.File
import java.security.MessageDigest

internal data class BookSourceFileTarget(
    val file: File,
    val relativePath: String
)

internal object BookSourceFileAccessPolicy {

    private const val sourceRootFolder = "source"
    private const val bookSourceIdentityPrefix = "book\u0000"

    fun resolveSourceRoot(cacheRoot: File, sourceUrl: String): File {
        val canonicalCacheRoot = cacheRoot.canonicalFile
        val namespace = namespace(sourceUrl)
        val sourceRoot = File(canonicalCacheRoot, "$sourceRootFolder${File.separator}$namespace")
            .canonicalFile
        requireStrictChild(canonicalCacheRoot, sourceRoot)
        return sourceRoot
    }

    fun resolvePath(sourceRoot: File, path: String): BookSourceFileTarget {
        val canonicalRoot = sourceRoot.canonicalFile
        if (path.isEmpty() || path == File.separator) {
            throw SecurityException("书源文件路径不可指向缓存根目录")
        }
        val target = if (isAbsolutePathInsideSourceRoot(canonicalRoot, path)) {
            File(path).canonicalFile
        } else {
            val relativePath = path.trimStart(File.separatorChar)
            File(canonicalRoot, relativePath).canonicalFile
        }
        requireStrictChild(canonicalRoot, target)
        return BookSourceFileTarget(
            file = target,
            relativePath = target.path.substring(canonicalRoot.path.length)
        )
    }

    fun requireContainedFile(sourceRoot: File, file: File): File {
        val canonicalRoot = sourceRoot.canonicalFile
        val canonicalFile = file.canonicalFile
        requireStrictChild(canonicalRoot, canonicalFile)
        return canonicalFile
    }

    fun requireContainedTree(sourceRoot: File, file: File) {
        val canonicalRoot = sourceRoot.canonicalFile
        requireContainedTree(canonicalRoot, file, hashSetOf())
    }

    private fun requireContainedTree(
        canonicalRoot: File,
        file: File,
        visitedDirectories: MutableSet<String>
    ) {
        val canonicalFile = file.canonicalFile
        requireStrictChild(canonicalRoot, canonicalFile)
        if (!canonicalFile.isDirectory || !visitedDirectories.add(canonicalFile.path)) {
            return
        }
        file.listFiles()?.forEach { child ->
            requireContainedTree(canonicalRoot, child, visitedDirectories)
        }
    }

    private fun namespace(sourceUrl: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest((bookSourceIdentityPrefix + sourceUrl).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun isAbsolutePathInsideSourceRoot(canonicalRoot: File, path: String): Boolean {
        val pathFile = File(path)
        if (!pathFile.isAbsolute) return false
        val rootPrefix = if (canonicalRoot.path.endsWith(File.separator)) {
            canonicalRoot.path
        } else {
            canonicalRoot.path + File.separator
        }
        return path == canonicalRoot.path || path.startsWith(rootPrefix)
    }

    private fun requireStrictChild(canonicalRoot: File, canonicalTarget: File) {
        val rootPrefix = if (canonicalRoot.path.endsWith(File.separator)) {
            canonicalRoot.path
        } else {
            canonicalRoot.path + File.separator
        }
        if (!canonicalTarget.path.startsWith(rootPrefix)) {
            throw SecurityException("书源文件路径超出缓存目录")
        }
    }
}
