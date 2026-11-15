package io.legado.app.help.pet

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PetDexInspectorTest {
    @Test fun validEntryInheritedEntryAndNestedEntryAreAcceptedWithoutExecution() {
        for (entry in listOf("PetEntry", "Factory\$NestedPet", "ThrowingPet")) {
            inspect("example.custom.$entry", "classes.dex" to PetDexFixtures.minimal())
        }
    }

    @Test fun crossDexInheritanceAndEntryInSecondaryDexAreAccepted() {
        inspect("example.custom.CrossEntry",
            "classes.dex" to PetDexFixtures.minimal(),
            "classes2.dex" to PetDexFixtures.bytes("cross-base.dex"),
            "classes3.dex" to PetDexFixtures.bytes("cross-entry.dex"))
    }

    @Test fun missingUnrelatedAbstractAndNonPublicConstructorsAreRejected() {
        for (entry in listOf("Missing", "Unrelated", "AbstractPet", "PrivatePet", "ArgumentPet")) {
            rejected { inspect("example.custom.$entry", "classes.dex" to PetDexFixtures.minimal()) }
        }
    }

    @Test fun magicOnlyTruncatedAndCorruptDexAreRejected() {
        for (bytes in listOf("dex\n035\u0000".toByteArray(), PetDexFixtures.minimal().copyOf(113),
            PetDexFixtures.minimal().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })) {
            rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
        }
    }

    @Test fun signedButInvalidHeaderAndSectionBoundsAreRejected() {
        for ((offset, value) in listOf(32 to 112, 36 to 120, 40 to 0x78563412,
            52 to Int.MAX_VALUE, 60 to Int.MAX_VALUE, 64 to Int.MAX_VALUE, 108 to 112)) {
            val bytes = PetDexFixtures.minimal()
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
            repairChecksums(bytes)
            rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
        }
        val bytes = PetDexFixtures.minimal()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(buffer.getInt(60), bytes.size - 1) // string item cannot end outside its mapped section
        repairChecksums(bytes)
        rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
    }

    @Test fun shaSignatureIsCheckedIndependentlyOfAdler32() {
        val bytes = PetDexFixtures.minimal()
        bytes[12] = (bytes[12].toInt() xor 1).toByte()
        val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, adler.value.toInt())
        rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
    }

    @Test fun duplicateClassesSdkDefinitionsAndHostReferencesAreRejected() {
        for (secondary in listOf(PetDexFixtures.minimal(), PetDexFixtures.bytes("sdk-shadow.dex"),
            PetDexFixtures.bytes("host-reference.dex"))) {
            rejected { inspect("example.custom.PetEntry", "classes.dex" to PetDexFixtures.minimal(), "classes2.dex" to secondary) }
        }
    }

    @Test fun nonStandardMultidexNamesAndGapsAreRejected() {
        for (name in listOf("classes1.dex", "classes02.dex", "classes3.dex")) {
            rejected { inspect("example.custom.PetEntry", "classes.dex" to PetDexFixtures.minimal(), name to PetDexFixtures.bytes("cross-base.dex")) }
        }
    }

    @Test fun signedInheritanceCycleAndClassDataOffsetsAreRejected() {
        for (case in 0..3) {
            val bytes = PetDexFixtures.minimal()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val owner = classDefinition(bytes, "Lexample/custom/PetEntry;")
            when (case) {
                0 -> buffer.putInt(owner + 8, buffer.getInt(owner))
                1 -> buffer.putInt(owner + 24, bytes.size - 1)
                2 -> buffer.putInt(owner + 12, bytes.size - 1)
                3 -> {
                    val data = buffer.getInt(owner + 24)
                    repeat(5) { bytes[data + it] = 0xff.toByte() }
                }
            }
            repairChecksums(bytes)
            rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
        }
    }

    @Test fun signedDuplicateMapTypesAndBadMethodOwnerAreRejected() {
        for (case in 0..1) {
            val bytes = PetDexFixtures.minimal()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (case == 0) buffer.putShort(buffer.getInt(52) + 16, 0)
            else buffer.putShort(buffer.getInt(92), 0xffff.toShort())
            repairChecksums(bytes)
            rejected { inspect("example.custom.PetEntry", "classes.dex" to bytes) }
        }
    }

    private fun classDefinition(bytes: ByteArray, descriptor: String): Int {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(buffer.getInt(96)) { index ->
            val at = buffer.getInt(100) + index * 32
            val type = buffer.getInt(at)
            val stringId = buffer.getInt(buffer.getInt(68) + type * 4)
            val stringAt = buffer.getInt(buffer.getInt(60) + stringId * 4)
            // These compiler fixtures use short ASCII class descriptors.
            check(bytes[stringAt].toInt() and 0x80 == 0)
            val value = String(bytes, stringAt + 1, bytes[stringAt].toInt(), Charsets.US_ASCII)
            if (value == descriptor) return at
        }
        error("Fixture class not found: $descriptor")
    }

    private fun inspect(entry: String, vararg dex: Pair<String, ByteArray>) {
        val file = File.createTempFile("pet-dex-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, bytes) in dex) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            }
            ZipFile(file).use { PetDexInspector.validate(it, PetPluginManifest(entryClass = entry)) }
        } finally { file.delete() }
    }

    private fun repairChecksums(bytes: ByteArray) {
        val signature = MessageDigest.getInstance("SHA-1").apply { update(bytes, 32, bytes.size - 32) }.digest()
        signature.copyInto(bytes, 12)
        val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, adler.value.toInt())
    }

    private fun rejected(block: () -> Unit) {
        assertTrue("Malformed package must fail static inspection", runCatching(block).isFailure)
    }
}
