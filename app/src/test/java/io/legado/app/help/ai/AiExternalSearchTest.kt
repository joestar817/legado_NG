package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingQueue

class AiExternalSearchTest {
    private val service = AiExternalSearchService(apiKey = "search-fixture", enabled = true)

    @Test
    fun selectsOneRouteAndTracksModelProviderAndProtocolChanges() {
        val official = AiDefaultProviders.all().single { it.id == "deepseek" }
        val senseTime = AiDefaultProviders.all().single { it.id == "sensenova" }
        val model = AiModel("deepseek-flash", abilities = listOf(AiModelAbility.TOOL))
        val pro = model.copy(id = "deepseek-v4-pro", modelSettings = AiModelSettings(
            official.modelConfigurationKey(), interfacePreference = AiModelInterfacePreference.RESPONSES,
        ))
        val auto = AiExternalSearchSettings(
            deepSeek = service,
            selectedService = service.provider.id,
            backupEnabled = true,
        )
        val nativeOnly = auto.copy(backupEnabled = false)
        val force = nativeOnly.copy(routing = AiWebRoutingMode.EXTERNAL)
        val unconfigured = AiExternalSearchSettings()
        val question = "刚发布了什么？"
        val link = "https://example.org/article"

        fun plan(
            settings: AiExternalSearchSettings,
            provider: AiProviderSetting,
            selected: AiModel = model,
            text: String = question,
            allowed: Boolean = true,
        ) = settings.resolveWebToolPlan(provider.deepSeekWebRoute(selected), selected, allowed, text, false)

        assertTrue(plan(auto, official) is AiWebToolPlan.Native)
        assertTrue(plan(auto, official, text = link) is AiWebToolPlan.Native)
        assertTrue(plan(auto, senseTime) is AiWebToolPlan.External)
        assertTrue(plan(auto, official, pro) is AiWebToolPlan.Native)
        assertTrue(plan(auto, official, pro, link) is AiWebToolPlan.External)
        assertTrue(plan(nativeOnly, official) is AiWebToolPlan.Native)
        assertTrue(plan(nativeOnly, official, pro, link) is AiWebToolPlan.Native)
        assertEquals(AiWebToolPlan.Off, plan(nativeOnly, senseTime))
        assertTrue(plan(force, official) is AiWebToolPlan.External)
        assertEquals(AiWebToolPlan.Off, plan(auto.copy(webEnabled = false), official))
        assertEquals(AiWebToolPlan.Off, plan(auto.copy(webEnabled = false), senseTime))
        assertEquals(AiWebToolPlan.Off, plan(force.copy(webEnabled = false), official))
        assertEquals(AiWebToolPlan.Off, plan(force.copy(selectedService = ""), official))
        assertEquals(AiWebToolPlan.Off, plan(auto, official, allowed = false))
        assertEquals(AiWebToolPlan.Off, plan(auto, senseTime, model.copy(abilities = emptyList())))
        assertEquals(AiWebToolPlan.Off, plan(unconfigured, senseTime))
        val external = AiWebAccess.External(service)
        assertEquals(AiWebAccess.Native, auto.webAccess(official, model))
        assertEquals(external, auto.webAccess(senseTime, model))
        assertEquals(AiWebAccess.Unavailable, nativeOnly.webAccess(senseTime, model))
        assertEquals(external, force.webAccess(official, model))
        assertEquals(AiWebAccess.Native, unconfigured.webAccess(official, model))
        assertEquals(AiWebAccess.Unavailable, unconfigured.webAccess(senseTime, model))
        assertEquals(AiWebAccess.Unavailable, auto.webAccess(senseTime, model.copy(abilities = emptyList())))
        assertEquals(AiWebAccess.Unavailable, force.copy(deepSeek = service.copy(enabled = false)).webAccess(official, model))
        assertEquals(AiWebAccess.Unavailable, force.copy(selectedService = "").webAccess(official, model))
        assertEquals(AiWebAccess.Unavailable, force.webAccess(null, null))
        assertEquals(external, auto.webAccess(official, pro, activeAccess = external))
        assertEquals(AiWebAccess.Disabled, auto.copy(webEnabled = false).webAccess(official, model, activeAccess = external))
    }

    @Test
    fun sendsOnlyFocusedQueryAndReturnsDistinctWebSources() = runBlocking {
        val api = Fixture(200, """{"results":[
            {"url":"https://example.org/a","title":"First","summary":"Long summary","snippet":"Short"},
            {"url":"https://EXAMPLE.org/a","title":"Duplicate"},
            {"url":"javascript:alert(0)","title":"Invalid link"},
            {"url":"https://example.org/b","snippet":"Second summary"},
            {"url":"https://example.org/c","title":"Beyond configured limit"}
        ]}""")
        val pages = api.client.execute(service.copy(resultCount = 2), AiExternalSearchTool.SEARCH, "  近日更新  ")

        val request = api.requests.remove()
        assertEquals("POST", request.method)
        assertEquals("https://api.deepseek.com/v1/tools/search", request.url.toString())
        assertEquals("Bearer search-fixture", request.header("Authorization"))
        assertEquals(JsonParser.parseString("""{"query":"近日更新","count":2}"""), request.payload())
        assertEquals(listOf(
            AiExternalWebPage(AiWebSource("https://example.org/a", "First"), "Long summary"),
            AiExternalWebPage(AiWebSource("https://example.org/b", "https://example.org/b"), "Second summary"),
        ), pages)
    }

    @Test
    fun doubaoSearchUsesCustomEndpointAndKeepsSummaryAndCardMetadata() = runBlocking {
        val doubao = AiExternalSearchService(provider = AiExternalSearchProvider.DOUBAO, apiKey = "doubao-key", resultCount = 25)
        val url = "https://example.org/book"
        val body = """{"ResponseMetadata":{"RequestId":"fixture"},"Result":{"ResultCount":1,"WebResults":[
            {"Url":"$url","Title":"目录","Snippet":"简短片段","Summary":"用于阅读的长摘要","SiteName":"书站","PublishTime":"2026-11-20T21:30:00+08:00"}
        ]}}"""
        val api = Fixture(200, body)
        val debug = api.client.searchForDebug(doubao, "  最近更新  ")
        val hit = requireNotNull(debug.data).single()
        assertEquals(url, hit.url)
        assertEquals("简短片段", hit.snippet)
        assertEquals("用于阅读的长摘要", hit.summary)
        assertEquals("书站", hit.siteName)
        assertEquals(LocalDate.of(2026, 11, 20), hit.publishedDate)
        assertEquals(body, debug.response?.body)
        val request = api.requests.remove()
        assertEquals("https://open.feedcoopapi.com/search_api/web_search", request.url.toString())
        assertEquals("Bearer doubao-key", request.header("Authorization"))
        val payload = request.payload().asJsonObject
        assertEquals("最近更新", payload["Query"].asString)
        assertEquals("web", payload["SearchType"].asString)
        assertEquals(25, payload["Count"].asInt)
        val page = api.client.execute(doubao, AiExternalSearchTool.SEARCH, "最近更新").single()
        assertEquals(AiExternalWebPage(AiWebSource(url, "目录"), "用于阅读的长摘要"), page)
        assertEquals(10, api.requests.remove().payload().asJsonObject["Count"].asInt)
        val zeroHits = Fixture(200, """{"Result":{"ResultCount":0,"WebResults":null}}""")
        assertEquals(emptyList<AiExternalSearchHit>(), zeroHits.client.searchForDebug(doubao, "无结果").data)
    }

    @Test
    fun doubaoGlobalUsesItsOwnEndpointAndDecodesTextFragments() = runBlocking {
        val doubao = AiExternalSearchService(
            provider = AiExternalSearchProvider.DOUBAO, apiKey = "doubao-key", resultCount = 50,
            doubaoMode = AiDoubaoSearchMode.GLOBAL,
        )
        val url = "https://example.org/release"
        val firstFragment = "𠮷".repeat(200)
        val body = """{"Result":{"ErrorCode":0,"TotalDocCount":47,"Documents":[
            {"Url":"$url","Title":"新版本","Snippet":[
                {"Type":"text","Text":"$firstFragment"},
                {"Type":"image","Image":{"ImageUrl":"https://example.org/image"}},
                {"Type":"text","Text":"发布说明"}],
                "HostInfo":{"Hostname":"example.org"},"DocumentInfo":{"PublishTime":"2026-11-22T12:00:00Z"}}
        ]}}"""
        val api = Fixture(200, body)
        val debug = api.client.searchForDebug(doubao, " 发布 ")
        val hit = requireNotNull(debug.data).single()
        assertEquals(url, hit.url)
        assertEquals("新版本", hit.title)
        assertEquals("$firstFragment…", hit.snippet)
        val summary = "$firstFragment\n发布说明"
        assertEquals(summary, hit.summary)
        assertEquals("example.org", hit.siteName)
        assertEquals(LocalDate.of(2026, 11, 22), hit.publishedDate)
        assertEquals(body, debug.response?.body)
        val request = api.requests.remove()
        assertEquals("https://open.feedcoopapi.com/search_api/global_search", request.url.toString())
        assertEquals("Bearer doubao-key", request.header("Authorization"))
        assertEquals(JsonParser.parseString(
            """{"Query":"发布","SearchType":"web","DocCount":20,"MaxSnippetLength":300,"MaxImageCountPerDoc":1}""",
        ), request.payload())
        assertEquals(AiExternalWebPage(AiWebSource(url, "新版本"), summary),
            api.client.execute(doubao, AiExternalSearchTool.SEARCH, "发布").single())
        assertEquals(10, api.requests.remove().payload().asJsonObject["DocCount"].asInt)

        val empty = Fixture(200, """{"Result":{"ErrorCode":0,"TotalDocCount":0}}""")
        assertEquals(emptyList<AiExternalSearchHit>(), empty.client.searchForDebug(doubao, "无结果").data)
        val errorBody = """{"Result":{"ErrorCode":123,"ErrorMsg":"Search failed","TotalDocCount":0,"Documents":[]}}"""
        val error = Fixture(200, errorBody).client.searchForDebug(doubao, "失败")
        assertEquals(AiExternalSearchFailure.REMOTE, error.error?.failure)
        assertEquals(errorBody, error.response?.body)
    }

    @Test
    fun doubaoFetchDistinguishesAWebPageFromApiAndTargetSiteErrors() = runBlocking {
        val doubao = AiExternalSearchService(provider = AiExternalSearchProvider.DOUBAO, apiKey = "doubao-key")
        val url = "https://example.org/book"
        val body = """{"Result":{"Data":{"StatusCode":200,"Url":"$url","Title":"目录","ContentText":"第一章"}}}"""
        val api = Fixture(200, body)
        val debug = api.client.fetchForDebug(doubao, url)
        val page = AiExternalWebPage(AiWebSource(url, "目录"), "第一章")
        assertEquals(page, debug.data)
        val request = api.requests.remove()
        assertEquals("https://open.feedcoopapi.com/search_api/fetch", request.url.toString())
        assertEquals(JsonParser.parseString("""{"Url":"$url"}"""), request.payload())
        val global = doubao.copy(doubaoMode = AiDoubaoSearchMode.GLOBAL)
        assertEquals(page, api.client.execute(global, AiExternalSearchTool.READ_LINK, url).single())
        assertEquals(request.url, api.requests.single().url)
        assertEquals(request.payload(), api.requests.single().payload())

        val missingPage = """{"Result":{"Data":{"StatusCode":404,"Url":"$url","ContentText":"Not found"}}}"""
        val missing = Fixture(200, missingPage).client.fetchForDebug(doubao, url)
        assertEquals(AiExternalSearchFailure.NO_PAGE, missing.error?.failure)
        assertEquals(missingPage, missing.response?.body)
        val quotaError = """{"ResponseMetadata":{"Error":{"CodeN":10406,"Code":"10406"}},"Result":null}"""
        val quota = Fixture(200, quotaError).client.searchForDebug(doubao, "关键词")
        assertEquals(AiExternalSearchFailure.BALANCE, quota.error?.failure)
        assertNull(quota.data)
    }

    @Test
    fun tavilyUsesBasicSearchAndOnlySendsTheSelectedUrlToExtract() = runBlocking {
        val tavily = AiExternalSearchService(provider = AiExternalSearchProvider.TAVILY, apiKey = "tavily-key", resultCount = 50)
        val url = "https://example.org/release"
        val firstPart = "𠮷".repeat(200)
        val fullPassage = firstPart + "The final update."
        val searchBody = """{"results":[{"title":"Release","url":"$url","content":"$fullPassage","score":0.9}]}"""
        val search = Fixture(200, searchBody)
        val debug = search.client.searchForDebug(tavily, " release ")
        val hit = requireNotNull(debug.data).single()
        assertEquals(firstPart + "…", hit.snippet)
        assertEquals(fullPassage, hit.summary)
        assertNull(hit.publishedDate)
        assertEquals(searchBody, debug.response?.body)
        val request = search.requests.remove()
        assertEquals("https://api.tavily.com/search", request.url.toString())
        assertEquals("Bearer tavily-key", request.header("Authorization"))
        val payload = request.payload().asJsonObject
        assertEquals("release", payload["query"].asString)
        assertEquals(20, payload["max_results"].asInt)
        assertEquals("basic", payload["search_depth"].asString)
        val page = search.client.execute(tavily, AiExternalSearchTool.SEARCH, "release").single()
        assertEquals(AiExternalWebPage(AiWebSource(url, "Release"), fullPassage), page)
        assertEquals(10, search.requests.remove().payload().asJsonObject["max_results"].asInt)
        val advanced = tavily.copy(tavilySearchDepth = AiTavilySearchDepth.ADVANCED)
        assertEquals(page, search.client.execute(advanced, AiExternalSearchTool.SEARCH, "release").single())
        assertEquals("advanced", search.requests.remove().payload().asJsonObject["search_depth"].asString)

        val fetchBody = """{"results":[{"url":"$url","raw_content":"# Release\n\nComplete text"}],"failed_results":[]}"""
        val fetch = Fixture(200, fetchBody)
        val article = requireNotNull(fetch.client.fetchForDebug(tavily, url).data)
        assertEquals(url, article.source.url)
        assertEquals("# Release\n\nComplete text", article.text)
        val basicRequest = fetch.requests.remove()
        assertEquals(article, fetch.client.execute(advanced, AiExternalSearchTool.READ_LINK, url).single())
        val fetchRequest = fetch.requests.single()
        assertEquals("https://api.tavily.com/extract", fetchRequest.url.toString())
        assertEquals(JsonParser.parseString("""["$url"]"""), fetchRequest.payload().asJsonObject["urls"])
        assertEquals("basic", fetchRequest.payload().asJsonObject["extract_depth"].asString)
        assertEquals(basicRequest.payload(), fetchRequest.payload())
        val failure = """{"results":[],"failed_results":[{"url":"$url","error":"Cannot extract content"}]}"""
        val failed = Fixture(200, failure).client.fetchForDebug(tavily, url)
        assertEquals(AiExternalSearchFailure.NO_PAGE, failed.error?.failure)
        assertEquals(failure, failed.response?.body)
    }

    @Test
    fun tinyFishSearchUsesChineseLocaleAndLimitsTheDisplayedPage() = runBlocking {
        val tinyFish = AiExternalSearchService(AiExternalSearchProvider.TINYFISH, apiKey = "tinyfish-key", resultCount = 1)
        val url = "https://example.org/book"
        val response = """{"results":[
            {"url":"$url","title":"书籍目录","snippet":"第一章","site_name":"书站","date":"2026-11-22"},
            {"url":"https://example.org/other-book","title":"Other book"}
        ],"page":0,"total_results":2}"""
        val api = Fixture(200, response)
        val debug = api.client.searchForDebug(tinyFish, "  夜无疆 & Legado  ")
        val hit = requireNotNull(debug.data).single()
        assertEquals(url, hit.url)
        assertEquals("书籍目录", hit.title)
        assertEquals("书站", hit.siteName)
        assertEquals("第一章", hit.summary)
        assertEquals(LocalDate.of(2026, 11, 22), hit.publishedDate)
        assertEquals(response, debug.response?.body)
        val request = api.requests.remove()
        assertEquals("GET", request.method)
        assertEquals("api.search.tinyfish.ai", request.url.host)
        assertEquals("/", request.url.encodedPath)
        assertEquals("tinyfish-key", request.header("X-API-Key"))
        assertNull(request.header("Authorization"))
        assertNull(request.body)
        assertEquals(setOf("query", "location", "language"), request.url.queryParameterNames)
        assertEquals("夜无疆 & Legado", request.url.queryParameter("query"))
        assertEquals("CN", request.url.queryParameter("location"))
        assertEquals("zh", request.url.queryParameter("language"))

        val chat = api.client.execute(tinyFish, AiExternalSearchTool.SEARCH, "𠮷")
        assertEquals(AiExternalWebPage(AiWebSource(url, "书籍目录"), "第一章"), chat.single())
        assertEquals("zh", api.requests.remove().url.queryParameter("language"))
        for (query in listOf("Legado release", "小説の新刊", "小說 소설")) {
            assertEquals(1, api.client.execute(tinyFish, AiExternalSearchTool.SEARCH, query).size)
            assertEquals(setOf("query"), api.requests.remove().url.queryParameterNames)
        }
    }

    @Test
    fun tinyFishReadLinkUsesFetchHostAndTheFinalUrl() = runBlocking {
        val tinyFish = AiExternalSearchService(AiExternalSearchProvider.TINYFISH, apiKey = "tinyfish-key")
        val url = "https://example.org/book"
        val finalUrl = "https://example.org/book/catalog"
        val response = """{"results":[{"url":"$url","final_url":"$finalUrl","title":"Book catalog","text":"# Catalog"}],"errors":[]}"""
        val api = Fixture(200, response)
        val page = AiExternalWebPage(AiWebSource(finalUrl, "Book catalog"), "# Catalog")
        assertEquals(page, api.client.fetchForDebug(tinyFish, url).data)
        val request = api.requests.remove()
        assertEquals("https://api.fetch.tinyfish.ai/", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("tinyfish-key", request.header("X-API-Key"))
        assertEquals(JsonParser.parseString("""{"urls":["$url"],"format":"markdown"}"""), request.payload())
        assertEquals(page, api.client.execute(tinyFish, AiExternalSearchTool.READ_LINK, url).single())

        val errorBody = """{"results":[],"errors":[{"url":"$url","error":"timeout"}]}"""
        val failed = Fixture(200, errorBody).client.fetchForDebug(tinyFish, url)
        assertEquals(AiExternalSearchFailure.TIMEOUT, failed.error?.failure)
        assertEquals(errorBody, failed.response?.body)
        val missing = """{"results":[],"errors":[{"url":"$url","error":"page_not_found","status":404}]}"""
        assertEquals(AiExternalSearchFailure.NO_PAGE, Fixture(200, missing).client.fetchForDebug(tinyFish, url).error?.failure)
    }

    @Test
    fun exaSearchUsesAutoAndKeepsHighlightsAndPublicationDate() = runBlocking {
        val exa = AiExternalSearchService(AiExternalSearchProvider.EXA, apiKey = "exa-key", resultCount = 100)
        val url = "https://example.org/release"
        val response = """{"results":[
            {"url":"$url","title":"Release","text":"Full page excerpt","highlights":["First",null," ","Second"],
             "publishedDate":"2026-11-22T01:00:00Z"},
            {"url":"https://example.org/docs","title":"Docs","text":"Fallback text"}
        ]}"""
        val api = Fixture(200, response)
        val hits = requireNotNull(api.client.searchForDebug(exa, "Release").data)
        assertEquals(listOf("First\nSecond", "Fallback text"), hits.map { it.summary })
        assertEquals(LocalDate.of(2026, 11, 22), hits.first().publishedDate)
        val request = api.requests.remove()
        assertEquals("https://api.exa.ai/search", request.url.toString())
        assertEquals("Bearer exa-key", request.header("Authorization"))
        val input = request.payload().asJsonObject
        assertEquals("Release", input["query"].asString)
        assertEquals("auto", input["type"].asString)
        assertEquals(100, input["numResults"].asInt)
        val contents = input.getAsJsonObject("contents")
        assertEquals(1200, contents.getAsJsonObject("highlights")["maxCharacters"].asInt)
        assertEquals(1200, contents.getAsJsonObject("text")["maxCharacters"].asInt)
        val pages = api.client.execute(exa, AiExternalSearchTool.SEARCH, "Release")
        assertEquals(AiExternalWebPage(AiWebSource(url, "Release"), "First\nSecond"), pages.first())
        assertEquals(10, api.requests.remove().payload().asJsonObject["numResults"].asInt)
    }

    @Test
    fun exaReadLinkUsesContentsAndPreservesFailedStatusesForDebugging() = runBlocking {
        val exa = AiExternalSearchService(AiExternalSearchProvider.EXA, apiKey = "exa-key")
        val url = "https://example.org/docs"
        val response = """{"results":[{"url":"$url","title":"Docs","text":"# Documentation"}],"statuses":[{"id":"$url","status":"success"}]}"""
        val api = Fixture(200, response)
        val page = AiExternalWebPage(AiWebSource(url, "Docs"), "# Documentation")
        assertEquals(page, api.client.fetchForDebug(exa, url).data)
        val request = api.requests.remove()
        assertEquals("https://api.exa.ai/contents", request.url.toString())
        assertEquals("Bearer exa-key", request.header("Authorization"))
        assertEquals(JsonParser.parseString("""{"urls":["$url"],"text":true}"""), request.payload())
        assertEquals(page, api.client.execute(exa, AiExternalSearchTool.READ_LINK, url).single())

        val missing = """{"results":[],"statuses":[{"id":"$url","status":"error","error":{"httpStatusCode":404}}]}"""
        val failed = Fixture(200, missing).client.fetchForDebug(exa, url)
        assertEquals(AiExternalSearchFailure.NO_PAGE, failed.error?.failure)
        assertEquals(missing, failed.response?.body)
    }

    @Test
    fun validatesQueryUsingTheSelectedProvidersLimit() = runBlocking {
        val doubao = AiExternalSearchService(provider = AiExternalSearchProvider.DOUBAO, apiKey = "doubao-key")
        val tavily = AiExternalSearchService(provider = AiExternalSearchProvider.TAVILY, apiKey = "tavily-key")
        val api = Fixture(200, """{"results":[]}""")
        val longQuery = "𠮷".repeat(400)
        assertFailure(AiExternalSearchFailure.INVALID_QUERY) {
            api.client.execute(doubao, AiExternalSearchTool.SEARCH, "𠮷".repeat(101))
        }
        assertFailure(AiExternalSearchFailure.INVALID_QUERY) {
            api.client.execute(tavily, AiExternalSearchTool.SEARCH, longQuery + "字")
        }
        assertTrue(api.requests.isEmpty())
        assertTrue(api.client.execute(tavily, AiExternalSearchTool.SEARCH, longQuery).isEmpty())
    }

    @Test
    fun fetchHasItsOwnPayloadAndOnlyAcceptsUsableMarkdown() = runBlocking {
        val api = Fixture(200, """{"title":"Release notes","content":"# Notes\n\nThe changes."}""")
        val link = "https://example.org/notes"
        val pages = api.client.execute(service, AiExternalSearchTool.READ_LINK, link)

        val request = api.requests.remove()
        assertEquals("https://api.deepseek.com/v1/tools/fetch", request.url.toString())
        assertEquals(JsonParser.parseString("""{"url":"$link"}"""), request.payload())
        assertEquals(AiExternalWebPage(AiWebSource(link, "Release notes"), "# Notes\n\nThe changes."), pages.single())

        val missing = Fixture(200, """{"title":"Release notes","content":""}""")
        assertFailure(AiExternalSearchFailure.NO_PAGE) {
            missing.client.execute(service, AiExternalSearchTool.READ_LINK, link)
        }
    }

    @Test
    fun distinguishesAnEmptyResultFromInvalidPayloadAndHttpFailures() = runBlocking {
        val empty = Fixture(200, """{"results":[]}""")
        assertTrue(empty.client.execute(service, AiExternalSearchTool.SEARCH, "test").isEmpty())
        val malformed = Fixture(200, """{"results":{}}""")
        assertFailure(AiExternalSearchFailure.RESPONSE) {
            malformed.client.execute(service, AiExternalSearchTool.SEARCH, "test")
        }
        val statuses = mapOf(
            401 to AiExternalSearchFailure.INVALID_KEY,
            402 to AiExternalSearchFailure.BALANCE,
            429 to AiExternalSearchFailure.RATE_LIMIT,
            504 to AiExternalSearchFailure.TIMEOUT,
        )
        for ((code, expected) in statuses) {
            val api = Fixture(code, """{"error":{"message":"remote error body"}}""")
            assertFailure(expected, code) { api.client.execute(service, AiExternalSearchTool.SEARCH, "test") }
            assertEquals(1, api.requests.size)
        }
    }

    @Test
    fun rejectsInvalidInputsLocallyAndCountsUnicodeCodePoints() = runBlocking {
        val api = Fixture(200, """{"results":[]}""")
        val query = "𠮷".repeat(128)
        assertFailure(AiExternalSearchFailure.INVALID_QUERY) {
            api.client.execute(service, AiExternalSearchTool.SEARCH, "${query}字")
        }
        assertFailure(AiExternalSearchFailure.INVALID_URL) {
            api.client.execute(service, AiExternalSearchTool.READ_LINK, "https://user:pass@example.org")
        }
        assertFailure(AiExternalSearchFailure.KEY_REQUIRED) {
            api.client.execute(service.copy(apiKey = ""), AiExternalSearchTool.SEARCH, "test")
        }
        assertTrue(api.requests.isEmpty())
        assertTrue(api.client.execute(service, AiExternalSearchTool.SEARCH, query).isEmpty())
        assertEquals(query, api.requests.remove().payload().asJsonObject.get("query").asString)
    }

    @Test
    fun debugSearchShowsEveryReturnedHitInOrderWithItsDisplayMetadata() = runBlocking {
        val body = """{"results":[
            {"url":"https://example.org/a","title":"First","snippet":"Short","summary":"Long","site_name":"Example","date":"2026-11-20T09:30:00Z"},
            {"url":"https://EXAMPLE.org/a","title":"Same URL","summary":"Fallback summary","site_name":null,"date":null},
            {"url":"javascript:alert(0)","title":"No clickable URL","snippet":"Still returned","date":"unknown"},
            {"url":"https://example.org/b","title":"Last"}
        ]}"""
        val api = Fixture(200, body)
        val result = api.client.searchForDebug(service.copy(resultCount = 2), "  query  ")

        assertEquals(listOf("First", "Same URL", "No clickable URL", "Last"), result.data?.map { it.title })
        assertEquals(listOf("https://example.org/a", "https://example.org/a", null, "https://example.org/b"),
            result.data?.map { it.url })
        assertEquals("Example", result.data?.first()?.siteName)
        assertEquals("Short", result.data?.first()?.snippet)
        assertEquals("Long", result.data?.first()?.summary)
        assertEquals(LocalDate.of(2026, 11, 20), result.data?.first()?.publishedDate)
        assertEquals("Fallback summary", result.data?.get(1)?.snippet)
        assertEquals("Fallback summary", result.data?.get(1)?.summary)
        assertEquals("javascript:alert(0)", result.data?.get(2)?.address)
        assertNull(result.data?.get(1)?.publishedDate)
        assertNull(result.data?.get(2)?.publishedDate)
        assertEquals(AiExternalSearchDebugResponse(200, body), result.response)
        assertTrue(result.elapsedMs >= 0)
        assertNull(result.error)
        assertEquals(1, api.requests.size)
        assertEquals(JsonParser.parseString("""{"query":"query","count":2}"""), api.requests.remove().payload())
    }

    @Test
    fun debugFetchKeepsFullMarkdownAndChatKeepsItsExistingLimit() = runBlocking {
        val markdown = "  \n# Notes\n\n${"正文\n".repeat(40_000)}\nEnd of article \n"
        val body = JsonObject().apply {
            addProperty("title", "Notes")
            addProperty("content", markdown)
        }.toString()
        val link = "https://example.org/notes"
        val api = Fixture(200, body)

        val debug = api.client.fetchForDebug(service, link)
        assertEquals(AiExternalWebPage(AiWebSource(link, "Notes"), markdown), debug.data)
        assertEquals(AiExternalSearchDebugResponse(200, body), debug.response)
        assertEquals(1, api.requests.size)
        assertEquals(JsonParser.parseString("""{"url":"$link"}"""), api.requests.remove().payload())

        val chat = api.client.execute(service, AiExternalSearchTool.READ_LINK, link).single()
        assertEquals(markdown.trim().take(100_000), chat.text)
        assertTrue(chat.truncated)
    }

    @Test
    fun debugKeepsFailedResponsesAndRedactsAnEchoedKey() = runBlocking {
        val key = "fixture-\"with-backslash\\"
        val response = JsonObject().apply { addProperty("message", "Rejected key $key") }.toString()
        val denied = Fixture(402, response).client.fetchForDebug(service.copy(apiKey = key), "https://example.org/a")
        assertEquals(AiExternalSearchFailure.BALANCE, denied.error?.failure)
        assertEquals(402, denied.response?.status)
        assertEquals("""{"message":"Rejected key [redacted]"}""", denied.response?.body)
        assertNull(denied.data)

        val malformed = Fixture(200, "non-JSON response").client.searchForDebug(service, "test")
        assertEquals(AiExternalSearchFailure.RESPONSE, malformed.error?.failure)
        assertEquals(AiExternalSearchDebugResponse(200, "non-JSON response"), malformed.response)
    }

    @Test
    fun debugDistinguishesEmptySearchFromInputAndResponseFailures() = runBlocking {
        val api = Fixture(200, """{"results":[]}""")
        val invalid = api.client.fetchForDebug(service, "invalid")
        assertEquals(AiExternalSearchFailure.INVALID_URL, invalid.error?.failure)
        assertNull(invalid.response)
        assertTrue(api.requests.isEmpty())

        val empty = api.client.searchForDebug(service, "empty")
        assertEquals(emptyList<AiExternalSearchHit>(), empty.data)
        assertNull(empty.error)
        assertEquals(200, empty.response?.status)

        val oversized = Fixture(200, "a".repeat(4 * 1024 * 1024 + 1)).client.searchForDebug(service, "test")
        assertEquals(AiExternalSearchFailure.RESPONSE, oversized.error?.failure)
        assertEquals(true, oversized.response?.truncated)
        assertEquals(16 * 1024, oversized.response?.body?.length)
    }

    @Test
    fun defaultsEnableEveryServiceAndRespectSavedSwitches() {
        val defaults = AiExternalSearchSettings()
        assertEquals(AiExternalSearchProvider.entries.toList(), defaults.services.map { it.provider })
        assertTrue(defaults.services.all { it.enabled })
        assertTrue(defaults.services.none { it.available })
        assertNull(defaults.activeService())

        val gson = Gson()
        val saved = """{"deepseek":{"api_key":"search-fixture","enabled":false}}"""
        val restored = gson.fromJson(saved, AiExternalSearchSettings::class.java)
        assertFalse(restored.deepSeek.enabled)
        assertEquals("search-fixture", restored.deepSeek.apiKey)
        assertNull(restored.activeService())
        assertEquals(defaults.services.size - 1, restored.services.count { it.enabled })
    }

    @Test
    fun savesGlobalWebSettingsAndOnlySelectsAvailableServices() {
        val gson = Gson()
        val recorded = """{"routing":"auto","selected_service":"deepseek",
            "deepseek":{"provider":"deepseek","api_key":"fixture","enabled":true}}"""
        val settings = gson.fromJson(recorded, AiExternalSearchSettings::class.java)
            .withServiceOrder(AiExternalSearchProvider.entries.reversed())
        assertTrue(settings.webEnabled)
        assertFalse(settings.backupEnabled)
        assertEquals("deepseek", settings.selectedService)

        assertSame(settings, settings.selectService(AiExternalSearchProvider.TAVILY))
        val unavailable = settings.withService(settings.deepSeek.copy(enabled = false))
        assertSame(unavailable, unavailable.selectService(AiExternalSearchProvider.DEEPSEEK))
        val paused = settings.copy(backupEnabled = true, webEnabled = false)
        val restored = gson.fromJson(gson.toJson(paused), AiExternalSearchSettings::class.java)
        assertFalse(restored.webEnabled)
        assertTrue(restored.backupEnabled)
        assertEquals("deepseek", restored.selectedService)
        assertEquals(settings.services, restored.services)
        assertFalse(restored.usesExternalService)
        assertTrue(restored.copy(webEnabled = true).usesExternalService)
    }

    @Test
    fun savesTheSearchServiceOrderWithoutChangingTheSelectedServiceOrItsSettings() {
        val tavily = AiExternalSearchService(
            provider = AiExternalSearchProvider.TAVILY,
            apiKey = "search-fixture",
            resultCount = 7,
        )
        val settings = AiExternalSearchSettings(
            routing = AiWebRoutingMode.EXTERNAL,
            selectedService = tavily.provider.id,
        ).withService(tavily)
        val reversedOrder = AiExternalSearchProvider.entries.reversed()
        val reordered = settings.withServiceOrder(reversedOrder)
        val gson = Gson()
        val json = gson.toJson(reordered)
        val restored = gson.fromJson(json, AiExternalSearchSettings::class.java)

        assertEquals(
            reversedOrder.map { it.id },
            JsonParser.parseString(json).asJsonObject.getAsJsonArray("service_order").map { it.asString },
        )
        assertEquals(reversedOrder, restored.services.map(AiExternalSearchService::provider))
        assertEquals(tavily, restored.activeService())
        assertEquals(settings.routing, restored.routing)
        assertEquals(settings.selectedService, restored.selectedService)
        for (provider in reversedOrder) {
            assertEquals(settings.service(provider), restored.service(provider))
        }
        assertSame(restored, restored.withServiceOrder(reversedOrder))
        assertSame(restored, restored.withServiceOrder(reversedOrder.dropLast(1)))
        assertSame(restored, restored.withServiceOrder(listOf(reversedOrder.first()) + reversedOrder.dropLast(1)))
    }

    @Test
    fun keepsEachKnownServiceOnceWhenRestoringAPartialSavedOrder() {
        val json = """{"service_order":["exa","unknown",null,"tavily","exa"]}"""
        val settings = Gson().fromJson(json, AiExternalSearchSettings::class.java)
        assertEquals(
            listOf(
                AiExternalSearchProvider.EXA,
                AiExternalSearchProvider.TAVILY,
                AiExternalSearchProvider.DEEPSEEK,
                AiExternalSearchProvider.DOUBAO,
                AiExternalSearchProvider.TINYFISH,
            ),
            settings.services.map(AiExternalSearchService::provider),
        )
    }

    @Test
    fun serializesServiceSettingsAndCanStillReadOldWebActivity() {
        val gson = Gson()
        val settings = AiExternalSearchSettings(AiWebRoutingMode.EXTERNAL, AiExternalSearchProvider.DEEPSEEK.id, service)
        val json = gson.toJson(settings)
        val objectValue = JsonParser.parseString(json).asJsonObject
        assertEquals("external", objectValue.get("routing").asString)
        assertEquals("deepseek", objectValue.get("selected_service").asString)
        assertEquals("search-fixture", objectValue.getAsJsonObject("deepseek").get("api_key").asString)
        assertEquals(settings, gson.fromJson(json, AiExternalSearchSettings::class.java))
        val old = gson.fromJson("""{"sources":[{"url":"https://example.org","title":"Title"}]}""", AiWebActivity::class.java)
        assertEquals("Title", old.settledOrNull()?.sources?.single()?.title)
        assertNull(old.providerLabel)
        assertNull(old.externalError)
    }

    @Test
    fun resultCountRangesRespectTheChatLimitAndEachTestEndpointsLimit() {
        for (provider in AiExternalSearchProvider.entries) {
            val settings = AiExternalSearchService(provider, resultCount = 7)
            assertEquals(1..10, settings.chatResultCountRange)
            assertEquals(7, settings.chatResultCount)
            val max = when (provider) {
                AiExternalSearchProvider.DEEPSEEK, AiExternalSearchProvider.DOUBAO -> 50
                AiExternalSearchProvider.TAVILY -> 20
                AiExternalSearchProvider.TINYFISH -> 10
                AiExternalSearchProvider.EXA -> 100
            }
            assertEquals(1..max, settings.resultCountRange)
            assertEquals(10, settings.copy(resultCount = max).chatResultCount)
        }
        val global = AiExternalSearchService(AiExternalSearchProvider.DOUBAO, doubaoMode = AiDoubaoSearchMode.GLOBAL)
        assertEquals(1..20, global.resultCountRange)
        assertEquals(1..10, global.chatResultCountRange)
    }

    @Test
    fun savesEachServiceIndependentlyAndOnlyRoutesToTheSelectedEnabledAccount() {
        val gson = Gson()
        val saved = """{"routing":"external","selected_service":"deepseek",
            "deepseek":{"name":"原搜索","api_key":"old-key","result_count":7,"enabled":true},
            "doubao":{"provider":"doubao","api_key":"doubao-key","enabled":true},
            "tavily":{"provider":"tavily","api_key":"tavily-key","enabled":true}}"""
        val original = gson.fromJson(saved, AiExternalSearchSettings::class.java)
        assertEquals("old-key", original.activeService()?.apiKey)
        assertEquals(AiExternalSearchProvider.entries.toList(), original.services.map { it.provider })
        val oldDoubao = original.service(AiExternalSearchProvider.DOUBAO)
        val oldTavily = original.service(AiExternalSearchProvider.TAVILY)
        assertEquals(AiDoubaoSearchMode.CUSTOM, oldDoubao.doubaoMode)
        assertEquals(AiTavilySearchDepth.BASIC, oldTavily.tavilySearchDepth)
        val doubao = oldDoubao.copy(doubaoMode = AiDoubaoSearchMode.GLOBAL)
        val tavily = oldTavily.copy(tavilySearchDepth = AiTavilySearchDepth.ADVANCED)
        val tinyFish = original.service(AiExternalSearchProvider.TINYFISH).copy(apiKey = "tinyfish-key", enabled = true)
        val exa = original.service(AiExternalSearchProvider.EXA).copy(apiKey = "exa-key", enabled = true)
        val selected = original.withService(doubao).withService(tavily).withService(tinyFish).withService(exa)
            .copy(selectedService = tavily.provider.id)
        val restored = gson.fromJson(gson.toJson(selected), AiExternalSearchSettings::class.java)
        assertEquals(tavily, restored.activeService())
        assertEquals(doubao, restored.copy(selectedService = doubao.provider.id).activeService())
        assertEquals(tinyFish, restored.copy(selectedService = tinyFish.provider.id).activeService())
        assertEquals(exa, restored.copy(selectedService = exa.provider.id).activeService())
        assertEquals(original.activeService(), restored.service(AiExternalSearchProvider.DEEPSEEK))
        assertNull(restored.withService(tavily.copy(enabled = false)).activeService())
        assertNull(restored.withService(tavily.copy(apiKey = "")).activeService())
        assertNull(restored.copy(selectedService = "").activeService())
    }

    private suspend fun assertFailure(
        expected: AiExternalSearchFailure,
        status: Int? = null,
        request: suspend () -> Unit,
    ) {
        try {
            request()
            fail("Expected ${expected.name}")
        } catch (error: AiExternalSearchException) {
            assertEquals(expected, error.failure)
            assertEquals(status, error.httpStatus)
        }
    }

    private class Fixture(code: Int, content: String) {
        val requests = LinkedBlockingQueue<Request>()
        val client = AiExternalSearchClient(OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Fixture")
                .body(content.toResponseBody("application/json".toMediaType()))
                .build()
        }.build())
    }

    private fun Request.payload() = JsonParser.parseString(Buffer().apply { body!!.writeTo(this) }.readUtf8())
}
