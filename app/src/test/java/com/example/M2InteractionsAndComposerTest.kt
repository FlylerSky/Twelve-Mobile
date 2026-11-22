package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.core.config.FirebaseAvailability
import com.example.core.network.AppError
import com.example.core.network.BackendErrorParser
import com.example.core.network.DefaultFirebaseTokenProvider
import com.example.core.util.ContentLimits
import com.example.core.util.QuillHtmlCodec
import com.example.data.local.SessionIdStore
import com.example.data.model.ContentCard
import com.example.data.model.ContentType
import com.example.data.model.ReactionType
import com.example.data.remote.FirestoreContentObserver
import com.example.data.remote.TwelveAuthApi
import com.example.data.remote.TwelveContentApi
import com.example.data.repository.AuthRepository
import com.example.data.repository.ContentActionHelper
import com.example.data.repository.ContentEvent
import com.example.data.repository.ContentEventsBus
import com.example.data.repository.ContentRepository
import com.example.data.repository.UserRepository
import com.example.data.remote.TwelveUsersApi
import com.example.ui.composer.ComposerResultEvent
import com.example.ui.composer.PostComposerViewModel
import com.example.ui.detail.ContentDetailViewModel
import com.example.ui.detail.DetailSideEffect
import com.example.ui.feed.FeedSideEffect
import com.example.ui.feed.FeedViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class M2InteractionsAndComposerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var contentRepository: ContentRepository
    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var eventsBus: ContentEventsBus
    private lateinit var actionHelper: ContentActionHelper
    private lateinit var firestoreObserver: FirestoreContentObserver

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

        val app = RuntimeEnvironment.getApplication()
        val unconfiguredFirebase = object : FirebaseAvailability {
            override val isConfigured: Boolean = false
        }
        val tokenProvider = DefaultFirebaseTokenProvider(unconfiguredFirebase)
        val authApi = retrofit.create(TwelveAuthApi::class.java)
        val contentApi = retrofit.create(TwelveContentApi::class.java)
        val usersApi = retrofit.create(TwelveUsersApi::class.java)

        authRepository = AuthRepository(app, unconfiguredFirebase, tokenProvider, authApi)
        contentRepository = ContentRepository(contentApi, SessionIdStore(app))
        userRepository = UserRepository(usersApi)
        eventsBus = ContentEventsBus()
        actionHelper = ContentActionHelper(contentRepository, authRepository, eventsBus)
        firestoreObserver = FirestoreContentObserver(unconfiguredFirebase)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) break
            kotlinx.coroutines.yield()
            Thread.sleep(10L)
        }
    }

    @Test
    fun `ContentLimits validates title, hashtags, comments and cover URL`() {
        assertNotNull(ContentLimits.validateTitle("Hi"))
        assertNull(ContentLimits.validateTitle("Tiêu đề hợp lệ"))

        val validTags = ContentLimits.parseAndValidateHashtags("#twelve #Android_16 #twelve")
        assertNull(validTags.error)
        assertEquals(listOf("#twelve", "#Android_16"), validTags.hashtags)

        val badTag = ContentLimits.parseAndValidateHashtags("#khong-hop-le")
        assertNotNull(badTag.error)

        assertNotNull(ContentLimits.validateCoverUrl("http://insecure.example/a.jpg"))
        assertNull(ContentLimits.validateCoverUrl("https://cdn.example.com/cover.jpg"))

        assertNotNull(ContentLimits.validateComment("   "))
        assertNotNull(ContentLimits.validateComment("a".repeat(ContentLimits.COMMENT_MAX + 1)))
        assertNull(ContentLimits.validateComment("Bình luận hợp lệ"))
    }

    @Test
    fun `QuillHtmlCodec produces sanitized Quill HTML, blocks javascript URLs, and roundtrips`() {
        val editor = """
            ## Tiêu đề phụ
            Đoạn **đậm** và *nghiêng* kèm [link](https://twelveta.vercel.app) và [xấu](javascript:alert(1)).

            > Trích dẫn 1

            - Mục A
            - Mục B

            ![Ảnh minh hoạ](https://cdn.example.com/img.png)
        """.trimIndent()

        val html = QuillHtmlCodec.toQuillHtml(editor)
        assertTrue(html.contains("<h2>Tiêu đề phụ</h2>"))
        assertTrue(html.contains("<strong>đậm</strong>"))
        assertTrue(html.contains("<em>nghiêng</em>"))
        assertTrue(html.contains("<a href=\"https://twelveta.vercel.app\""))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("<blockquote>Trích dẫn 1</blockquote>"))
        assertTrue(html.contains("<ul><li>Mục A</li><li>Mục B</li></ul>"))
        assertTrue(html.contains("<img src=\"https://cdn.example.com/img.png\""))

        val backToText = QuillHtmlCodec.fromHtmlToEditorText(html)
        assertTrue(backToText.contains("## Tiêu đề phụ"))
        assertTrue(backToText.contains("**đậm**"))
        assertTrue(backToText.contains("- Mục A"))
    }

    @Test
    fun `FeedViewModel optimistic reaction updates and rolls back on 429 error`() =
        runTest(mainDispatcherRule.dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "posts": [
                        {
                          "id": "p1",
                          "title": "Bài gốc",
                          "likeCount": 10,
                          "dislikeCount": 2,
                          "commentCount": 1,
                          "viewCount": 50,
                          "shareCount": 0,
                          "postScore": 100,
                          "myReaction": null
                        }
                      ],
                      "nextCursor": null
                    }
                    """.trimIndent(),
                ),
            )
            // Lần bấm thứ nhất: LIKE thành công
            server.enqueue(MockResponse().setBody("""{"myReaction":"like"}"""))
            // Lần bấm thứ hai: 429 rate limited -> phải rollback về LIKE + likeCount=11
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .addHeader("Retry-After", "30")
                    .setBody("""{"error":"Bạn thao tác quá nhanh, thử lại sau 30 giây."}"""),
            )

            val vm = FeedViewModel(contentRepository, SavedStateHandle(), actionHelper, eventsBus)
            awaitCondition { !vm.uiState.value.isInitialLoading }
            val initialCard = vm.uiState.value.items.first()
            assertNull(initialCard.reaction)
            assertEquals(10, initialCard.likeCount)

            val messages = mutableListOf<String>()
            val collector = launch {
                vm.sideEffects.collect { eff ->
                    if (eff is FeedSideEffect.ShowMessage) messages.add(eff.message)
                }
            }

            vm.react(initialCard, ReactionType.LIKE)
            awaitCondition { vm.uiState.value.items.first().reaction == ReactionType.LIKE && server.requestCount == 2 }
            assertEquals(11, vm.uiState.value.items.first().likeCount)

            val likedCard = vm.uiState.value.items.first()
            vm.react(likedCard, ReactionType.DISLIKE)
            awaitCondition { messages.isNotEmpty() }

            // Đã hoàn tác lại đúng trạng thái LIKE trước khi bấm DISLIKE
            val afterRollback = vm.uiState.value.items.first()
            assertEquals(ReactionType.LIKE, afterRollback.reaction)
            assertEquals(11, afterRollback.likeCount)
            assertEquals(2, afterRollback.dislikeCount)
            assertTrue(messages.last().contains("30"))
            collector.cancel()
        }

    @Test
    fun `ContentDetailViewModel supports comments CRUD, share link, and PIN_LIMIT_REACHED error`() =
        runTest(mainDispatcherRule.dispatcher) {
            var commentCalls = 0
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/api/posts/p10/comments/c2") && request.method == "PATCH" ->
                            MockResponse().setBody("""{"message":"Đã cập nhật"}""")
                        path.startsWith("/api/posts/p10/comments/c2") && request.method == "DELETE" ->
                            MockResponse().setBody("""{"message":"Đã xoá"}""")
                        path.startsWith("/api/posts/p10/comments") && request.method == "GET" ->
                            MockResponse().setBody(
                                """
                                {
                                  "comments": [
                                    {
                                      "id": "c1",
                                      "content": "Bình luận ban đầu",
                                      "isEdited": false,
                                      "createdAt": "2026-01-01T00:00:00Z",
                                      "updatedAt": "2026-01-01T00:00:00Z"
                                    }
                                  ],
                                  "nextCursor": null,
                                  "serverTimeMs": 1760000000000
                                }
                                """.trimIndent(),
                            )
                        path.startsWith("/api/posts/p10/comments") && request.method == "POST" -> {
                            commentCalls++
                            MockResponse().setResponseCode(201).setBody("""{"message":"OK","commentId":"c2"}""")
                        }
                        path.startsWith("/api/posts/p10/share") && request.method == "POST" ->
                            MockResponse().setBody("""{"shareUrl":"https://twelveta.vercel.app/post/p10?idu=u1&s=sig"}""")
                        path.startsWith("/api/posts/p10/pin") && request.method == "PUT" ->
                            MockResponse()
                                .setResponseCode(400)
                                .setBody("""{"error":"Bạn chỉ có thể ghim tối đa 3 bài viết.","code":"PIN_LIMIT_REACHED"}""")
                        path.startsWith("/api/posts/p10") && request.method == "GET" ->
                            MockResponse().setBody(
                                """
                                {
                                  "post": {
                                    "id": "p10",
                                    "title": "Chi tiết bài viết",
                                    "contentHtml": "<p>Xin chào</p>",
                                    "likeCount": 1,
                                    "dislikeCount": 0,
                                    "commentCount": 1,
                                    "viewCount": 5,
                                    "shareCount": 0,
                                    "postScore": 10
                                  },
                                  "myReaction": null
                                }
                                """.trimIndent(),
                            )
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

            val vm = ContentDetailViewModel(
                contentRepository = contentRepository,
                userRepository = userRepository,
                authRepository = authRepository,
                firestoreObserver = firestoreObserver,
                eventsBus = eventsBus,
            )

            val effects = mutableListOf<DetailSideEffect>()
            val effJob = launch { vm.sideEffects.collect { effects.add(it) } }

            vm.openContent(ContentType.POST, "p10")
            awaitCondition { !vm.uiState.value.isLoading && !vm.uiState.value.isLoadingComments }
            assertEquals(1, vm.uiState.value.comments.size)

            vm.onCommentInputChanged("Bình luận mới")
            vm.sendComment()
            awaitCondition { vm.uiState.value.comments.size == 2 && !vm.uiState.value.isSendingComment }
            assertEquals("c2", vm.uiState.value.comments.last().id)
            assertEquals(2, vm.uiState.value.content?.commentCount)

            vm.editComment("c2", "Đã chỉnh sửa", "Cập nhật thành công")
            awaitCondition { vm.uiState.value.comments.last().content == "Đã chỉnh sửa" }
            assertTrue(vm.uiState.value.comments.last().isEdited)

            vm.deleteComment("c2", "Đã xoá thành công")
            awaitCondition { vm.uiState.value.comments.size == 1 && !vm.uiState.value.isLoading }
            assertEquals(1, vm.uiState.value.content?.commentCount)

            vm.share()
            awaitCondition { effects.any { it is DetailSideEffect.OpenShareSheet } }
            val shareEff = effects.filterIsInstance<DetailSideEffect.OpenShareSheet>().first()
            assertEquals("https://twelveta.vercel.app/post/p10?idu=u1&s=sig", shareEff.shareUrl)

            vm.togglePin(currentlyPinned = false, pinnedOkMsg = "Đã ghim", unpinnedOkMsg = "Đã bỏ ghim")
            awaitCondition { effects.filterIsInstance<DetailSideEffect.ShowMessage>().any { it.message.contains("tối đa 3") } }
            effJob.cancel()
        }

    @Test
    fun `PostComposerViewModel creates post and maps edit mode fields and CONTENT_TOO_COMPLEX error`() =
        runTest(mainDispatcherRule.dispatcher) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody("""{"message":"Đã đăng bài.","postId":"new_post_99"}"""),
            )

            val createVm = PostComposerViewModel(contentRepository, authRepository, eventsBus)
            createVm.init(ContentType.POST, null)
            createVm.onTitleChanged("Bài viết từ Mobile")
            createVm.onHashtagsChanged("#twelve #mobile")
            createVm.onBodyChanged("Nội dung **rất hay**.")

            var createdId: String? = null
            val evJob = launch {
                createVm.events.collect { ev ->
                    if (ev is ComposerResultEvent.Created) createdId = ev.id
                }
            }
            createVm.submit("Nội dung trống")
            awaitCondition { createdId != null }
            assertEquals("new_post_99", createdId)
            val req = server.takeRequest()
            assertEquals("/api/posts", req.path)
            val bodyStr = req.body.readUtf8()
            assertTrue(bodyStr.contains("Bài viết từ Mobile"))
            assertTrue(bodyStr.contains("<strong>rất hay</strong>"))
            evJob.cancel()

            // Edit mode + server CONTENT_TOO_COMPLEX mapping
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "post": {
                        "id": "p55",
                        "title": "Tiêu đề cũ",
                        "contentHtml": "<p>Đoạn cũ</p>",
                        "coverImageUrl": "https://cdn.example.com/c.jpg",
                        "hashtags": ["#tag1"]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"error":"Nội dung quá phức tạp.","code":"CONTENT_TOO_COMPLEX"}"""),
            )
            val editVm = PostComposerViewModel(contentRepository, authRepository, eventsBus)
            editVm.init(ContentType.POST, "p55")
            awaitCondition { !editVm.uiState.value.isLoadingInitial }
            assertEquals("Tiêu đề cũ", editVm.uiState.value.title)
            assertEquals("Đoạn cũ", editVm.uiState.value.bodyText)

            editVm.onBodyChanged("Nội dung mới")
            editVm.submit("Nội dung trống")
            awaitCondition { editVm.uiState.value.submitError != null }
            assertEquals("Nội dung quá phức tạp.", editVm.uiState.value.submitError)
        }
}
