package io.legado.app.help.font

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class FontDocumentDeletionTest {
    private fun withProvider(action: (FontDeletionQueryProvider, Uri, Uri) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "font.deletion.${UUID.randomUUID()}"
        val provider = FontDeletionQueryProvider()
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "fonts")
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, "fonts/font.ttf")
        action(provider, document, tree)
    }

    @Test fun aDirectoryStillListingTheTargetCannotReportSuccess() = withProvider { provider, document, tree ->
        provider.childIds = listOf("fonts/font.ttf")
        val failure = assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
        assertTrue(failure.message.orEmpty().contains("仍存在"))
    }

    @Test fun anUnlistedButStillQueryableDocumentCannotReportSuccess() = withProvider { provider, document, tree ->
        provider.documentPresent = true
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun aCompleteDirectoryConfirmsAbsenceWhenTheRemovedDocumentReturnsNull() = withProvider { provider, document, tree ->
        // DocumentsProvider.query may return null after FileNotFoundException.
        provider.documentPresent = null
        FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
    }

    @Test fun aCompleteDirectoryConfirmsAbsenceAfterTheDocumentGrantIsRevoked() = withProvider { provider, document, tree ->
        provider.denyDocumentQuery = true
        FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
    }

    @Test fun anEmptyCompleteSingleDocumentCursorConfirmsAbsence() = withProvider { _, document, _ ->
        FontDirectoryCatalog.confirmDocumentDeleted(document, null)
    }

    @Test fun aNullSingleDocumentCursorCannotProveAbsence() = withProvider { provider, document, _ ->
        provider.documentPresent = null
        val failure = assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, null)
        }
        assertTrue(failure.message.orEmpty().contains("无法确认"))
    }

    @Test fun aRevokedSingleDocumentGrantCannotProveAbsence() = withProvider { provider, document, _ ->
        provider.denyDocumentQuery = true
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, null)
        }
    }

    @Test fun aNullDirectoryCursorCannotProveAbsence() = withProvider { provider, document, tree ->
        provider.directoryAvailable = false
        assertThrows(IllegalArgumentException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun aLoadingDirectoryCannotProveAbsence() = withProvider { provider, document, tree ->
        provider.directoryExtras.putBoolean(DocumentsContract.EXTRA_LOADING, true)
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun aDirectoryReportingAnErrorCannotProveAbsence() = withProvider { provider, document, tree ->
        provider.directoryExtras.putString(DocumentsContract.EXTRA_ERROR, "provider unavailable")
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun aLoadingSingleDocumentCursorCannotProveAbsence() = withProvider { provider, document, _ ->
        provider.documentExtras.putBoolean(DocumentsContract.EXTRA_LOADING, true)
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, null)
        }
    }

    @Test fun aSingleDocumentCursorReportingAnErrorCannotProveAbsence() = withProvider { provider, document, _ ->
        provider.documentExtras.putString(DocumentsContract.EXTRA_ERROR, "provider unavailable")
        assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, null)
        }
    }

    @Test fun aCompleteDirectoryConfirmsAbsenceWhenTheDeletedTreeUriFailsMembershipChecks() = withProvider { provider, document, tree ->
        // FileSystemProvider embeds FileNotFoundException in this message without retaining a cause.
        provider.documentQueryError = IllegalArgumentException(
            "Failed to determine if fonts/font.ttf is child of fonts: java.io.FileNotFoundException: Missing file",
        )
        FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
    }

    @Test fun aMissingFileQueryErrorWithoutDirectoryEvidenceCannotProveAbsence() = withProvider { provider, document, _ ->
        provider.documentQueryError = IllegalArgumentException("java.io.FileNotFoundException: Missing file")
        val failure = assertThrows(IllegalStateException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, null)
        }
        assertTrue(failure.message.orEmpty().contains("无法确认"))
    }

    @Test fun cancellationIsNotHiddenByCompleteDirectoryEvidence() = withProvider { provider, document, tree ->
        provider.documentQueryError = CancellationException("cancelled query")
        assertThrows(CancellationException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun aMalformedDocumentCursorIsNotHiddenByCompleteDirectoryEvidence() = withProvider { provider, document, tree ->
        provider.invalidDocumentColumns = true
        provider.documentPresent = true
        assertThrows(IllegalArgumentException::class.java) {
            FontDirectoryCatalog.confirmDocumentDeleted(document, tree.toString())
        }
    }

    @Test fun failedVerificationIsNotCountedAsDeletedAndDoesNotStopTheNextFile() = withProvider { provider, document, tree ->
        provider.childIds = listOf("fonts/font.ttf")
        val other = DocumentsContract.buildDocumentUriUsingTree(tree, "fonts/other.ttf")
        val entries = listOf(document, other).map { uri ->
            FontFileEntry(FontDirectoryCatalog.identity(uri.toString()), "font.ttf", uri.toString(), 1,
                managed = null, canDelete = true, directoryUri = tree.toString(), sha256 = "a".repeat(64))
        }
        val deleted = mutableListOf<String>()
        val result = runBlocking {
            FontBatchDeletion.delete(entries, onDeleted = { deleted += it.id }) {
                FontDirectoryCatalog.confirmDocumentDeleted(Uri.parse(it.uri), it.directoryUri)
            }
        }
        assertEquals(listOf(entries[0].id), result.failures.map { it.entry.id })
        assertEquals(listOf(entries[1].id), result.deleted.map { it.id })
        assertEquals(listOf(entries[1].id), deleted)
    }
}

/** Simulates both stale provider results and Android's missing-document/null-query behavior. */
internal class FontDeletionQueryProvider : ContentProvider() {
    var childIds: List<String> = emptyList()
    var directoryAvailable = true
    var documentPresent: Boolean? = false
    var denyDocumentQuery = false
    var documentQueryError: Exception? = null
    var invalidDocumentColumns = false
    val directoryExtras = Bundle()
    val documentExtras = Bundle()

    override fun onCreate() = true
    override fun getType(uri: Uri) = "font/ttf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val children = uri.lastPathSegment == "children"
        if (!children) documentQueryError?.let { throw it }
        if (!children && denyDocumentQuery) throw SecurityException("document grant revoked")
        if (children && !directoryAvailable || !children && documentPresent == null) return null
        val columns = if (!children && invalidDocumentColumns) arrayOf("unexpected_column") else projection?.map { it }?.toTypedArray()
            ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val cursor = object : MatrixCursor(columns) {
            override fun getExtras() = if (children) directoryExtras else documentExtras
        }
        val ids = if (children) childIds else if (documentPresent == true)
            listOf(DocumentsContract.getDocumentId(uri)) else emptyList()
        ids.forEach { id ->
            cursor.addRow(columns.map { column ->
                if (column == DocumentsContract.Document.COLUMN_DOCUMENT_ID) id else null
            }.toTypedArray())
        }
        return cursor
    }
}
