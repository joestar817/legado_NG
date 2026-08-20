package io.legado.app.help.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class BookSourceFileAccessPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sourceRootUsesExactSourceUrl() {
        val cacheRoot = temporaryFolder.newFolder("cache")

        val lmRoot = BookSourceFileAccessPolicy.resolveSourceRoot(
            cacheRoot,
            "https://novel.example#lm"
        )
        val qdRoot = BookSourceFileAccessPolicy.resolveSourceRoot(
            cacheRoot,
            "https://novel.example#qd"
        )

        assertEquals(File(cacheRoot, "source").canonicalFile, lmRoot.parentFile)
        assertEquals(File(cacheRoot, "source").canonicalFile, qdRoot.parentFile)
        assertNotEquals(lmRoot, qdRoot)
    }

    @Test
    fun returnedRelativePathRemainsReadable() {
        val sourceRoot = temporaryFolder.newFolder("source")
        val download = BookSourceFileAccessPolicy.resolvePath(
            sourceRoot,
            "0123456789abcdef.txt"
        )

        val readBack = BookSourceFileAccessPolicy.resolvePath(
            sourceRoot,
            download.relativePath
        )

        assertEquals(download.file, readBack.file)
        assertEquals("${File.separator}0123456789abcdef.txt", download.relativePath)
    }

    @Test
    fun safeNestedPathRemainsSupported() {
        val sourceRoot = temporaryFolder.newFolder("source")

        val target = BookSourceFileAccessPolicy.resolvePath(
            sourceRoot,
            "0123456789abcdef.audio${File.separator}mpeg"
        )

        assertEquals(
            File(sourceRoot, "0123456789abcdef.audio${File.separator}mpeg").canonicalFile,
            target.file
        )
    }

    @Test
    fun absolutePathInsideSourceRootIsAllowed() {
        val sourceRoot = temporaryFolder.newFolder("source")
        val absoluteFile = File(sourceRoot, "folder${File.separator}chapter.txt")

        val target = BookSourceFileAccessPolicy.resolvePath(
            sourceRoot,
            absoluteFile.absolutePath
        )

        assertEquals(absoluteFile.canonicalFile, target.file)
        assertEquals(
            "${File.separator}folder${File.separator}chapter.txt",
            target.relativePath
        )
    }

    @Test
    fun absolutePathWithParentTraversalOutsideSourceRootIsRejected() {
        val parent = temporaryFolder.newFolder("parent")
        val sourceRoot = File(parent, "source").apply { mkdirs() }
        val escapingPath = File(
            sourceRoot,
            "folder${File.separator}..${File.separator}..${File.separator}outside.txt"
        ).absolutePath

        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(sourceRoot, escapingPath)
        }
    }

    @Test
    fun traversalAndSourceRootAreRejected() {
        val sourceRoot = temporaryFolder.newFolder("source")

        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(
                sourceRoot,
                "..${File.separator}..${File.separator}files${File.separator}backup.zip"
            )
        }
        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(sourceRoot, "")
        }
        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(sourceRoot, File.separator)
        }
    }

    @Test
    fun siblingDirectoryWithSamePrefixIsRejected() {
        val parent = temporaryFolder.newFolder("parent")
        val sourceRoot = File(parent, "source-a").apply { mkdirs() }

        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(
                sourceRoot,
                "..${File.separator}source-a-copy${File.separator}payload"
            )
        }
    }

    @Test
    fun symbolicLinkEscapeIsRejectedWhenSupported() {
        val sourceRoot = temporaryFolder.newFolder("source")
        val outside = temporaryFolder.newFolder("outside")
        val link = sourceRoot.toPath().resolve("linked")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link, outside.toPath())
        }.isSuccess
        assumeTrue("当前环境不支持创建符号链接", linkCreated)

        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.resolvePath(
                sourceRoot,
                "linked${File.separator}payload"
            )
        }
    }

    @Test
    fun nestedSymbolicLinkEscapeIsRejectedBeforeDirectoryOperations() {
        val sourceRoot = temporaryFolder.newFolder("source")
        val folder = File(sourceRoot, "folder").apply { mkdirs() }
        val outside = temporaryFolder.newFolder("outside")
        val link = folder.toPath().resolve("linked")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link, outside.toPath())
        }.isSuccess
        assumeTrue("当前环境不支持创建符号链接", linkCreated)

        assertThrows(SecurityException::class.java) {
            BookSourceFileAccessPolicy.requireContainedTree(sourceRoot, folder)
        }
    }
}
