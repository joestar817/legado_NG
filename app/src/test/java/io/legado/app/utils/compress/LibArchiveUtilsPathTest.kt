package io.legado.app.utils.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class LibArchiveUtilsPathTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun nestedEntryStaysInsideDestination() {
        val destination = temporaryFolder.newFolder("archive")

        val entry = LibArchiveUtils.resolveArchiveEntry(
            destination,
            "folder${File.separator}chapter.txt",
            false
        )

        assertEquals(
            File(destination, "folder${File.separator}chapter.txt").canonicalFile,
            entry
        )
    }

    @Test
    fun parentAndSamePrefixSiblingEntriesAreRejected() {
        val destination = temporaryFolder.newFolder("archive")

        assertThrows(SecurityException::class.java) {
            LibArchiveUtils.resolveArchiveEntry(
                destination,
                "..${File.separator}outside.txt",
                false
            )
        }
        assertThrows(SecurityException::class.java) {
            LibArchiveUtils.resolveArchiveEntry(
                destination,
                "..${File.separator}archive-copy${File.separator}outside.txt",
                false
            )
        }
    }

    @Test
    fun destinationRootCanOnlyBeADirectoryEntry() {
        val destination = temporaryFolder.newFolder("archive")

        assertEquals(
            destination.canonicalFile,
            LibArchiveUtils.resolveArchiveEntry(destination, ".", true)
        )
        assertThrows(SecurityException::class.java) {
            LibArchiveUtils.resolveArchiveEntry(destination, ".", false)
        }
    }

    @Test
    fun symbolicLinkEscapeIsRejectedWhenSupported() {
        val destination = temporaryFolder.newFolder("archive")
        val outside = temporaryFolder.newFolder("outside")
        val link = destination.toPath().resolve("linked")
        val linkCreated = runCatching {
            Files.createSymbolicLink(link, outside.toPath())
        }.isSuccess
        assumeTrue("当前环境不支持创建符号链接", linkCreated)

        assertThrows(SecurityException::class.java) {
            LibArchiveUtils.resolveArchiveEntry(
                destination,
                "linked${File.separator}chapter.txt",
                false
            )
        }
    }
}
