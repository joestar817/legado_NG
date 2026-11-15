package io.legado.app.help.font

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontReferenceIdentityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun plainAndFileUriIdentifyTheSameFileIncludingSpacesAndHashSigns() {
        val file = File(temporary.root, "字体 #1.ttf")
        assertEquals(FontReferenceIdentity.normalize(file.path), FontReferenceIdentity.normalize(file.toURI().toString()))
    }

    @Test fun treeAndSingleDocumentUrisMatchWithoutEquatingDifferentProviders() {
        val tree = "content://fonts.example/tree/root/document/folder%2Ffont.ttf"
        val single = "content://fonts.example/document/folder%2Ffont.ttf"
        assertEquals(FontReferenceIdentity.normalize(tree), FontReferenceIdentity.normalize(single))
        assertNotEquals(FontReferenceIdentity.normalize(tree), FontReferenceIdentity.normalize(single.replace("fonts.example", "other.example")))
    }

    @Test fun literalPlusIsNotDecodedAsSpace() {
        assertEquals(FontReferenceIdentity.normalize("content://fonts/document/a+b.ttf"),
            FontReferenceIdentity.normalize("content://fonts/document/a%2Bb.ttf"))
        assertNotEquals(FontReferenceIdentity.normalize("content://fonts/document/a+b.ttf"),
            FontReferenceIdentity.normalize("content://fonts/document/a%20b.ttf"))
    }

    @Test fun primaryStorageDocumentMatchesTheActualPathButTraversalCannotEscapeRoot() {
        val root = temporary.newFolder("storage")
        val file = File(root, "Fonts/font.ttf")
        assertEquals(file.canonicalPath, FontReferenceIdentity.normalize(
            "content://com.android.externalstorage.documents/tree/primary%3AFonts/document/primary%3AFonts%2Ffont.ttf", root.path))
        val outside = File(root.parentFile, "outside.ttf")
        assertNotEquals(outside.canonicalPath, FontReferenceIdentity.normalize(
            "content://com.android.externalstorage.documents/document/primary%3A..%2Foutside.ttf", root.path))
    }

    @Test fun differentFilesRemainDifferentEvenWithSameNamesOrBytes() {
        val left = File(temporary.newFolder("left"), "font.ttf").apply { writeText("same") }
        val right = File(temporary.newFolder("right"), "font.ttf").apply { writeText("same") }
        assertNotEquals(FontReferenceIdentity.normalize(left.path), FontReferenceIdentity.normalize(right.path))
    }

    @Test fun verifiedSecondaryVolumeMatchesRawTreeAndSingleDocumentReferences() {
        val root = temporary.newFolder("1234-5678")
        val file = File(root, "Fonts/字体 #1.ttf")
        val volumes = mapOf("1234-5678" to root.path)
        val single = "content://com.android.externalstorage.documents/document/1234-5678%3AFonts%2F%E5%AD%97%E4%BD%93%20%231.ttf"
        val tree = single.replace("/document/", "/tree/1234-5678%3AFonts/document/")
        assertEquals(file.canonicalPath, FontReferenceIdentity.normalize(single, volumeRoots = volumes))
        assertEquals(file.canonicalPath, FontReferenceIdentity.normalize(tree, volumeRoots = volumes))
        assertNotEquals(file.canonicalPath, FontReferenceIdentity.normalize(single))
    }
}
