package io.legado.app.ui.book.character

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.ui.design.components.compose.NgDrawerBackdropSource
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCharacterDetailActivity : AppCompatActivity() {
    private var character by mutableStateOf<BookCharacter?>(null)
    private var voiceCatalog by mutableStateOf(BookCharacterVoiceCatalog())
    private var voiceCatalogJob: Job? = null
    private val voiceSheetDebouncer = TtsSheetLaunchDebouncer()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0x50000000),
            navigationBarStyle = SystemBarStyle.dark(0x50000000),
        )
        val workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY).orEmpty()
        val characterId = intent.getLongExtra(BookCharacterActivity.EXTRA_CHARACTER_ID, 0L)
        if (workKey.isBlank() || characterId <= 0L) {
            finish()
            return
        }
        val automaticStyle = intent.automaticCharacterSilhouetteStyle()
        setContent {
            NgAppTheme(updateSystemBars = false) {
                val silhouetteStyle = rememberBookCharacterSilhouetteStyle().resolve(automaticStyle)
                val voiceBindings by remember(workKey, characterId) {
                    appDb.bookCharacterDao.flowTtsBindings(workKey)
                        .map { bindings ->
                            bindings.filter {
                                it.targetType == BookCharacterTtsBinding.TargetType.CHARACTER &&
                                    it.targetId == characterId
                            }
                        }
                        .distinctUntilChanged()
                        .flowOn(IO)
                }.collectAsStateWithLifecycle(initialValue = emptyList())
                character?.let { value ->
                    val voiceBinding = voiceBindings.firstOrNull { it.engineId == AppConfig.multiRoleTtsEngineId }
                    BookCharacterDetail(
                        character = value,
                        silhouetteStyle = silhouetteStyle,
                        voiceSummary = getString(
                            R.string.character_tts_speaker_summary,
                            voiceCatalog.roleVoiceName(this, voiceBinding, value.gender),
                        ),
                        onBack = ::finish,
                        onEdit = {
                            showDialogFragment(BookCharacterEditDialog(value.workKey, value.id))
                        },
                        onChooseVoice = {
                            if (voiceSheetDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
                                showBookCharacterVoiceSheet(
                                    title = value.name,
                                    target = BookCharacterTtsBinding.character(value.workKey, value.id),
                                    binding = voiceBinding,
                                    onCatalogRefreshed = ::reloadVoiceCatalog,
                                )
                            }
                        },
                    )
                } ?: Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        lifecycleScope.launch {
            appDb.bookCharacterDao.flowCharacters(workKey)
                .map { characters -> characters.firstOrNull { it.id == characterId } }
                .distinctUntilChanged()
                .collect { current ->
                    if (current == null) finish() else character = current
                }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadVoiceCatalog()
    }

    private fun reloadVoiceCatalog() {
        voiceCatalogJob?.cancel()
        voiceCatalogJob = lifecycleScope.launch {
            voiceCatalog = withContext(IO) { BookCharacterVoiceCatalog.loadMultiRole() }
        }
    }

    companion object {
        internal fun open(
            context: Context,
            workKey: String,
            characterId: Long,
            automaticSilhouetteStyle: BookCharacterSilhouetteStyle,
        ) {
            context.startActivity(Intent(context, BookCharacterDetailActivity::class.java).apply {
                putExtra(BookCharacterActivity.EXTRA_WORK_KEY, workKey)
                putExtra(BookCharacterActivity.EXTRA_CHARACTER_ID, characterId)
                putAutomaticCharacterSilhouetteStyle(automaticSilhouetteStyle)
            })
        }
    }
}

@Composable
private fun BookCharacterDetail(
    character: BookCharacter,
    silhouetteStyle: BookCharacterSilhouetteStyle,
    voiceSummary: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChooseVoice: () -> Unit,
) {
    val context = LocalContext.current
    val portrait = remember(character.portraitUri) { BookCharacterPortraits.localUri(character.portraitUri) }
    val silhouette = silhouetteStyle.forRole(character.roleTag, hasPortrait = portrait != null)
    val role = BookCharacterLabels.roleLabel(context, character.roleTag)
    val controlBackground = Color.White.copy(alpha = 0.78f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val infoHeight = maxHeight * 0.42f
        NgDrawerBackdropSource(Modifier.matchParentSize()) {
            BookCharacterPortraitImage(
                portrait = portrait,
                silhouette = silhouette,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.systemBars)
                .background(Color.Black.copy(alpha = 0.30f))
        )
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(start = 9.dp, top = 2.dp, end = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).background(controlBackground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_left_20),
                            contentDescription = stringResource(R.string.back),
                            tint = Color(0xFF191D24),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 36.dp)
                            .background(controlBackground, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val controlColor = Color(0xFF4D6599)
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = controlColor)
                        Text(
                            stringResource(R.string.edit),
                            color = controlColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp)
                    .heightIn(max = infoHeight).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.57f))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 17.dp, top = 10.dp, end = 17.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        character.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (character.roleTag != BookCharacter.RoleTag.UNKNOWN) {
                        Box(
                            modifier = Modifier.heightIn(min = 18.dp)
                                .background(Color(0x806078A7), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(role, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("character_detail_voice")
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.character_tts_change_voice),
                            onClick = onChooseVoice,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val voiceColor = Color.White.copy(alpha = 0.90f)
                    Icon(
                        painter = painterResource(R.drawable.ic_tts_tab_voice),
                        contentDescription = null,
                        tint = voiceColor,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = voiceSummary,
                        color = voiceColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right_20),
                        contentDescription = null,
                        tint = voiceColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
                character.displayIntro()?.let {
                    Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp)
                }
                val aliases = remember(character.aliasesJson) {
                    character.aliases().filter(String::isNotBlank)
                }
                if (aliases.isNotEmpty()) {
                    Text(
                        stringResource(R.string.character_detail_aliases, aliases.joinToString(" · ")),
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}
