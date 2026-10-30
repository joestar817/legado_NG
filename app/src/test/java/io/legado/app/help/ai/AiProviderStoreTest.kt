package io.legado.app.help.ai

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AiProviderStoreTest {

    @Before
    fun setUp() {
        context.injectAsAppCtx()
    }

    @Test
    fun `loading saved providers keeps user disabled state`() {
        withRestoredProviders {
            val disabledProviders = AiDefaultProviders.all().map { it.copy(enabled = false) }
            preferences()
                .edit()
                .putString(PreferKey.aiProvidersJson, GSON.toJson(disabledProviders))
                .commit()

            val loaded = AiProviderStore.providers()

            assertEquals(
                AiDefaultProviders.all().map { it.id }.toSet(),
                loaded.map { it.id }.toSet()
            )
            assertTrue(loaded.none { it.enabled })
        }
    }

    @Test
    fun `custom provider default name uses base url host`() {
        withRestoredProviders {
            preferences().edit().remove(PreferKey.aiProvidersJson).commit()

            assertEquals(
                "api.openai.com",
                AiProviderStore.createCustomProvider(AiProviderType.OPENAI).name
            )
            assertEquals(
                "api.anthropic.com",
                AiProviderStore.createCustomProvider(AiProviderType.CLAUDE).name
            )
        }
    }

    @Test
    fun `reset built in provider restores defaults and keeps key enabled and models`() {
        withRestoredProviders {
            val providers = AiDefaultProviders.all()
            val modified = providers.first { it.id == "deepseek" }.copy(
                name = "My DeepSeek",
                baseUrl = "https://proxy.example.com/v1",
                apiKey = "secret",
                enabled = false,
                timeoutSeconds = 30,
                model = "custom-model",
                models = listOf(AiModel("custom-model")),
                availableModelIds = listOf("custom-model"),
                availableModelSelectionInitialized = true,
                streamResponseEnabled = true,
                useCustomModelsUrl = true,
                modelsUrl = "/custom/models"
            )
            AiProviderStore.saveProviders(
                providers.map { if (it.id == modified.id) modified else it }
            )

            val reset = requireNotNull(AiProviderStore.resetProvider("deepseek"))

            assertEquals("DeepSeek", reset.name)
            assertEquals("https://api.deepseek.com", reset.baseUrl)
            assertEquals("/chat/completions", reset.chatCompletionsPath)
            assertEquals(180, reset.timeoutSeconds)
            assertFalse(reset.streamResponseEnabled)
            assertFalse(reset.useCustomModelsUrl)
            assertEquals("/models", reset.modelsUrl)
            assertEquals("secret", reset.apiKey)
            assertFalse(reset.enabled)
            assertEquals("custom-model", reset.model)
            assertEquals(listOf("custom-model"), reset.models.map { it.id })
            assertEquals(listOf("custom-model"), reset.availableModelIds)
            assertTrue(reset.availableModelSelectionInitialized)
        }
    }

    @Test
    fun `reset custom provider keeps manual name and follows auto name`() {
        withRestoredProviders {
            preferences().edit().remove(PreferKey.aiProvidersJson).commit()
            val custom = AiProviderStore.createCustomProvider(AiProviderType.OPENAI)

            AiProviderStore.saveProvider(
                custom.copy(
                    name = "My Proxy",
                    baseUrl = "https://proxy.example.com/v1",
                    apiKey = "secret",
                    enabled = false
                )
            )
            val manualNameReset = requireNotNull(AiProviderStore.resetProvider(custom.id))
            assertEquals("My Proxy", manualNameReset.name)
            assertEquals("https://api.openai.com/v1", manualNameReset.baseUrl)
            assertEquals("secret", manualNameReset.apiKey)
            assertFalse(manualNameReset.enabled)

            AiProviderStore.saveProvider(
                manualNameReset.copy(
                    name = "proxy.example.com",
                    baseUrl = "https://proxy.example.com/v1"
                )
            )
            val autoNameReset = requireNotNull(AiProviderStore.resetProvider(custom.id))
            assertEquals("api.openai.com", autoNameReset.name)
        }
    }

    private fun preferences() = context.defaultSharedPreferences

    private fun withRestoredProviders(block: () -> Unit) {
        val prefs = preferences()
        val original = prefs.getString(PreferKey.aiProvidersJson, null)
        try {
            block()
        } finally {
            prefs.edit().apply {
                if (original == null) {
                    remove(PreferKey.aiProvidersJson)
                } else {
                    putString(PreferKey.aiProvidersJson, original)
                }
            }.commit()
        }
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
}
