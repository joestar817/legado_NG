package io.legado.app.ui.rss.article

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.about.LegacyLogToolbarAction
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadRecordDialog(private val origin: String? = null) : DialogFragment() {

    private val viewModel by viewModels<RssSortViewModel>()
    private var records by mutableStateOf<List<RssReadRecord>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadRecordPanel(
                    records = records,
                    onOpen = { record ->
                        ReadRss.readRss(requireActivity() as AppCompatActivity, record)
                        dismiss()
                    },
                    onClear = {
                        viewModel.deleteAllRecord(origin)
                        records = emptyList()
                    }
                )
            }
        }
        lifecycleScope.launch {
            records = withContext(IO) { viewModel.getRecords(origin) }
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.82f))
    }
}

@Composable
private fun ReadRecordPanel(
    records: List<RssReadRecord>,
    onOpen: (RssReadRecord) -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(dimensionResource(R.dimen.ng_dialog_radius)),
        color = Color(NgTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.read_record),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LegacyLogToolbarAction(
                    text = stringResource(R.string.clear),
                    color = Color(LocalContext.current.accentColor),
                    enabled = records.isNotEmpty(),
                    onClick = { confirmClear = true },
                )
            }
            if (records.isEmpty()) {
                RssEmptyState(
                    stringResource(R.string.empty),
                    Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(records, key = { it.origin + '\u0000' + it.record }) { record ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(record) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(NgTheme.colors.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = record.title.orEmpty().ifBlank { record.record },
                                    color = Color(NgTheme.colors.onSurface),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = record.record,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = Color(NgTheme.colors.onSurfaceVariant),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.read_record)) },
            text = {
                Text("${stringResource(R.string.sure_del)}\n${records.size} ${stringResource(R.string.read_record)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
