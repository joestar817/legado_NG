package io.legado.app.help.storage

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingTimeBackupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun roundTripKeepsExactLongValuesAndUsesStableKeys() {
        val snapshot = ReadingTimeBackup.Snapshot(
            Long.MAX_VALUE,
            linkedMapOf(21000L to Long.MAX_VALUE, 20000L to 0L),
        )
        val encoded = ReadingTimeBackup.encode(snapshot)

        assertEquals(snapshot, ReadingTimeBackup.decode(encoded))
        val json = JsonParser.parseString(encoded).asJsonObject
        assertEquals(setOf("version", "totalReadTime", "days"), json.keySet())
        assertEquals(20000L, json.getAsJsonArray("days")[0].asJsonObject.get("epochDay").asLong)
    }

    @Test fun missingTimingFileIsDistinguishableFromExplicitZeroTotal() {
        val root = temp.newFolder()
        File(root, "readRecord.json").writeText("""[{"bookName":"book","readTime":999}]""")

        assertNull(ReadingTimeBackup.read(root))
    }

    @Test fun legacyArchivePreservesItsHistoricalTotalWithoutInventingCalendarDays() {
        val imported = ReadingTimeBackup.snapshotForRestore(null, listOf(120L, 300L, 45L))

        assertEquals(ReadingTimeBackup.Snapshot(465L, emptyMap()), imported)
        assertNull(ReadingTimeBackup.snapshotForRestore(null, null))
        assertEquals(
            ReadingTimeBackup.Snapshot(0L, emptyMap()),
            ReadingTimeBackup.snapshotForRestore(null, emptyList()),
        )
    }

    @Test fun repeatedLegacyImportProducesSameCumulativeValueRatherThanAnotherIncrement() {
        val archive = listOf(120L, 300L, 45L)
        val first = ReadingTimeBackup.snapshotForRestore(null, archive)!!
        val repeated = ReadingTimeBackup.snapshotForRestore(null, archive)!!

        assertEquals(first, repeated)
        assertEquals(465L, maxOf(first.totalReadTime, repeated.totalReadTime))
        assertEquals(900L, maxOf(900L, repeated.totalReadTime))
    }

    @Test fun legacyImportRejectsNegativeValuesAndOverflowBeforeAnyMerge() {
        assertThrows(IllegalArgumentException::class.java) {
            ReadingTimeBackup.snapshotForRestore(null, listOf(100L, -1L))
        }
        assertThrows(ArithmeticException::class.java) {
            ReadingTimeBackup.snapshotForRestore(null, listOf(Long.MAX_VALUE, 1L))
        }
        assertEquals(
            Long.MAX_VALUE,
            ReadingTimeBackup.snapshotForRestore(null, listOf(Long.MAX_VALUE, 0L))!!.totalReadTime,
        )
    }

    @Test fun explicitNewTimingSnapshotTakesPrecedenceOverBookSum() {
        val snapshot = ReadingTimeBackup.Snapshot(20L, mapOf(20000L to 10L))

        assertEquals(snapshot, ReadingTimeBackup.snapshotForRestore(snapshot, listOf(120L, 300L)))
    }

    @Test fun presentEmptyOrMalformedFileIsAnErrorRatherThanZero() {
        val root = temp.newFolder()
        val file = File(root, ReadingTimeBackup.fileName)
        listOf("", "null", "[]", "{", "{}").forEach { raw ->
            file.writeText(raw)
            assertThrows(Exception::class.java) { ReadingTimeBackup.read(root) }
        }
    }

    @Test fun rejectsUnsupportedVersionsMissingFieldsAndDuplicateDays() {
        listOf(
            """{"version":2,"totalReadTime":0,"days":[]}""",
            """{"version":1,"days":[]}""",
            """{"version":1,"totalReadTime":0}""",
            """{"version":1,"totalReadTime":0,"days":[],"other":0}""",
            """{"version":1,"totalReadTime":0,"days":null}""",
            """{"version":1,"totalReadTime":0,"days":[{"epochDay":1,"readTime":2},{"epochDay":1,"readTime":3}]}""",
            """{"version":1,"totalReadTime":0,"days":[{"epochDay":1}]}""",
        ).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { ReadingTimeBackup.decode(raw) }
        }
    }

    @Test fun rejectsNonIntegerNegativeStringAndOverflowValues() {
        listOf("-1", "-0", "1.0", "1e3", "9223372036854775808", "\"1\"", "true", "null").forEach { value ->
            listOf(
                """{"version":$value,"totalReadTime":0,"days":[]}""",
                """{"version":1,"totalReadTime":$value,"days":[]}""",
                """{"version":1,"totalReadTime":0,"days":[{"epochDay":$value,"readTime":0}]}""",
                """{"version":1,"totalReadTime":0,"days":[{"epochDay":0,"readTime":$value}]}""",
            ).forEach { raw ->
                assertThrows(IllegalArgumentException::class.java) { ReadingTimeBackup.decode(raw) }
            }
        }
    }

    @Test fun timingFileBelongsToBooksAndIsVerifiedByNativeManifest() {
        val root = temp.newFolder()
        File(root, "config.xml").writeText("<map />")
        File(root, ReadingTimeBackup.fileName).writeText(
            ReadingTimeBackup.encode(ReadingTimeBackup.Snapshot(123L, mapOf(20000L to 100L))),
        )
        File(root, BackupResources.MANIFEST).writeText(
            """{"version":1,"modules":["books"],"files":{}}""",
        )
        BackupResources.finishManifest(root, listOf("config.xml", ReadingTimeBackup.fileName))

        assertEquals(BackupModule.BOOKS, BackupModules.fileModule(ReadingTimeBackup.fileName))
        assertEquals(setOf(BackupModule.BOOKS.id), BackupResources.validate(root))
        File(root, ReadingTimeBackup.fileName).appendText("damaged")
        assertThrows(IllegalArgumentException::class.java) { BackupResources.validate(root) }
    }

    @Test fun archiveCannotSmuggleTimingFileThroughAnotherModule() {
        val root = temp.newFolder()
        File(root, "config.xml").writeText("<map />")
        File(root, ReadingTimeBackup.fileName).writeText(
            ReadingTimeBackup.encode(ReadingTimeBackup.Snapshot(0L, emptyMap())),
        )
        File(root, BackupResources.MANIFEST).writeText(
            """{"version":1,"modules":["other"],"files":{}}""",
        )
        BackupResources.finishManifest(root, listOf("config.xml", ReadingTimeBackup.fileName))

        assertThrows(IllegalArgumentException::class.java) { BackupResources.validate(root) }
    }
}
