package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.data.local.SessionIdStore
import com.example.data.model.ContentType
import com.example.data.remote.TwelveContentApi
import com.example.data.remote.TwelveTrendingApi
import com.example.data.remote.TwelveUsersApi
import com.example.data.repository.ContentRepository
import com.example.data.repository.ScoreMode
import com.example.data.repository.SearchSort
import com.example.data.repository.SearchType
import com.example.data.repository.TrendingRepository
import com.example.data.repository.UserRepository
import com.example.ui.feed.FeedViewModel
import com.example.ui.trending.HashtagSectionTab
import com.example.ui.trending.TrendingMainTab
import com.example.ui.trending.TrendingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class M1RepositoriesAndViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var contentApi: TwelveContentApi
    private lateinit var usersApi: TwelveUsersApi
    private lateinit var trendingApi: TwelveTrendingApi
    private lateinit var contentRepository: ContentRepository
    private lateinit var userRepository: UserRepository
    private lateinit var trendingRepository: TrendingRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        contentApi = retrofit.create(TwelveContentApi::class.java)
        usersApi = retrofit.create(TwelveUsersApi::class.java)
        trendingApi = retrofit.create(TwelveTrendingApi::class.java)

        val app = RuntimeEnvironment.getApplication()
        contentRepository = ContentRepository(contentApi, SessionIdStore(app))
        userRepository = UserRepository(usersApi)
        trendingRepository = TrendingRepository(trendingApi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `ContentRepository parses cursor feed and passes share query params on detail`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "posts": [
                    {
                      "id": "p1",
                      "title": "Bài viết thử",
                      "excerpt": "Đoạn tóm tắt",
                      "hashtags": ["#twelve"],
                      "likeCount": 5,
                      "dislikeCount": 1,
                      "commentCount": 2,
                      "viewCount": 10,
                      "shareCount": 0,
                      "postScore": 14,
                      "createdAt": "2026-01-01T00:00:00Z",
                      "author": {
                        "uid": "u1",
                        "displayName": "Minh",
                        "tagName": "@minh",
                        "badges": ["pro"]
                      }
                    }
                  ],
                  "nextCursor": "p1"
                }
                """.trimIndent(),
            ),
        )

        val page = contentRepository.listPosts(cursor = null, limit = 10).getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals("p1", page.nextCursor)
        assertEquals("@minh", page.items.first().author?.tagName)

        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "post": {
                    "id": "p1",
                    "title": "Bài viết thử",
                    "contentHtml": "<p>Nội dung chi tiết</p>",
                    "hashtags": ["#twelve"],
                    "likeCount": 5,
                    "dislikeCount": 1,
                    "commentCount": 2,
                    "viewCount": 11,
                    "aiViewCount": 3,
                    "shareCount": 0,
                    "postScore": 15,
                    "createdAt": "2026-01-01T00:00:00Z"
                  },
                  "myReaction": "like"
                }
                """.trimIndent(),
            ),
        )

        val detail = contentRepository.getContentDetail(
            type = ContentType.POST,
            id = "p1",
            sharerUid = "sharer_1",
            shareSignature = "sig_abc",
            sharedFlag = false,
        ).getOrThrow()
        assertEquals("Bài viết thử", detail.content.title)
        assertEquals(3, detail.content.aiViewCount)

        val req1 = server.takeRequest()
        assertTrue(req1.path!!.startsWith("/api/posts?limit=10"))
        val req2 = server.takeRequest()
        assertTrue(req2.path!!.contains("idu=sharer_1"))
        assertTrue(req2.path!!.contains("s=sig_abc"))
        assertTrue(req2.path!!.contains("sid="))
    }

    @Test
    fun `UserRepository normalizes tag before calling by-tag endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {
                    "uid": "u2",
                    "displayName": "Lan Anh",
                    "tagName": "@lan_anh",
                    "accountGroup": "SA",
                    "badges": ["sa"],
                    "bio": "Xin chào"
                  }
                }
                """.trimIndent(),
            ),
        )

        val profile = userRepository.getPublicProfileByTag("@Lan_Anh").getOrThrow()
        assertEquals("u2", profile.uid)
        assertTrue(profile.isSA)
        val req = server.takeRequest()
        assertEquals("/api/users/by-tag/lan_anh", req.path)
    }

    private suspend fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) {
                break
            }
            kotlinx.coroutines.yield()
            Thread.sleep(10L)
        }
    }

    @Test
    fun `FeedViewModel merges cursor pages without duplicates and stops when nextCursor is null`() =
        runTest(mainDispatcherRule.dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """{"posts":[{"id":"p1","title":"Bài 1"}],"nextCursor":"p1"}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"posts":[{"id":"p1","title":"Bài 1"},{"id":"p2","title":"Bài 2"}],"nextCursor":null}""",
                ),
            )

            val vm = FeedViewModel(contentRepository, SavedStateHandle())
            awaitCondition { !vm.uiState.value.isInitialLoading }
            assertEquals(listOf("p1"), vm.uiState.value.items.map { it.id })
            assertTrue(vm.uiState.value.hasMore)

            vm.loadMore()
            awaitCondition { !vm.uiState.value.isLoadingMore && vm.uiState.value.items.size == 2 }
            assertEquals(listOf("p1", "p2"), vm.uiState.value.items.map { it.id })
            assertFalse(vm.uiState.value.hasMore)
            assertNull(vm.uiState.value.nextCursor)
        }

    @Test
    fun `TrendingRepository and TrendingViewModel debounce search and handle hashtag query`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Initial loadWeeklyUsers in TrendingViewModel.init
            server.enqueue(
                MockResponse().setBody("""{"weekCode":"2026-W02","ranking":[]}"""),
            )
            server.enqueue(
                MockResponse().setBody("""{"weekCode":"2026-W02","mode":"classic","ranking":[]}"""),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "query": "compose",
                      "type": "post",
                      "sort": "newest",
                      "mode": "classic",
                      "posts": [
                        { "id": "p9", "title": "Jetpack Compose", "type": "post" }
                      ],
                      "hashtags": []
                    }
                    """.trimIndent(),
                ),
            )

            val vm = TrendingViewModel(trendingRepository)
            awaitCondition { !vm.uiState.value.isLoadingWeeklyUsers }

            vm.selectMainTab(TrendingMainTab.HASHTAGS)
            awaitCondition { !vm.uiState.value.isLoadingHashtagWeekly }
            vm.selectHashtagSection(HashtagSectionTab.SEARCH)

            vm.onSearchQueryChanged("comp")
            advanceTimeBy(150L)
            vm.onSearchQueryChanged("compose")
            advanceTimeBy(450L)
            advanceUntilIdle()
            awaitCondition { vm.uiState.value.searchResult != null && !vm.uiState.value.isSearching }

            assertEquals(1, vm.uiState.value.searchResult?.posts?.size)
            assertEquals("p9", vm.uiState.value.searchResult?.posts?.first()?.id)

            // Ensure Search direct repository call strips # in hashtag posts query
            server.enqueue(
                MockResponse().setBody("""{"tag":"#android","mode":"advanced","posts":[]}"""),
            )
            trendingRepository.getHashtagPosts("#Android", ScoreMode.ADVANCED).getOrThrow()
            var lastPath = ""
            repeat(server.requestCount) {
                lastPath = server.takeRequest().path.orEmpty()
            }
            assertEquals("/api/trending/hashtags/android/posts?mode=advanced", lastPath)
            assertEquals("post", SearchType.POST.apiValue)
            assertEquals("trending", SearchSort.TRENDING.apiValue)
        }
}
