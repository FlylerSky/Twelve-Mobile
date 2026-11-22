package com.example.data.remote

import com.example.data.model.AddCommentResponse
import com.example.data.model.CommentMutationRequest
import com.example.data.model.CreateBlogResponse
import com.example.data.model.CreateContentRequest
import com.example.data.model.CreatePostResponse
import com.example.data.model.GetBlogResponse
import com.example.data.model.GetPostResponse
import com.example.data.model.ListBlogsResponse
import com.example.data.model.ListCommentsResponse
import com.example.data.model.ListPostsResponse
import com.example.data.model.MessageResponse
import com.example.data.model.PinPostResponse
import com.example.data.model.ReactRequest
import com.example.data.model.ReactResponse
import com.example.data.model.ShareResponse
import com.example.data.model.UpdateContentRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API đọc & tương tác bài viết, blog, bình luận, reaction, share và ghim bài.
 * Nguồn đối chiếu: backend/src/routes/posts.routes.js, blog.routes.js, frontend/src/api/content.api.js.
 */
interface TwelveContentApi {
    @GET("posts")
    suspend fun listPosts(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 10,
    ): ListPostsResponse

    @POST("posts")
    suspend fun createPost(
        @Body body: CreateContentRequest,
    ): CreatePostResponse

    @GET("posts/{id}")
    suspend fun getPost(
        @Path("id") id: String,
        @Query("idu") idu: String? = null,
        @Query("s") signature: String? = null,
        @Query("shared") shared: String? = null,
        @Query("sid") sid: String? = null,
    ): GetPostResponse

    @PATCH("posts/{id}")
    suspend fun updatePost(
        @Path("id") id: String,
        @Body body: UpdateContentRequest,
    ): MessageResponse

    @DELETE("posts/{id}")
    suspend fun deletePost(
        @Path("id") id: String,
    ): MessageResponse

    @PUT("posts/{id}/pin")
    suspend fun pinPost(
        @Path("id") id: String,
    ): PinPostResponse

    @DELETE("posts/{id}/pin")
    suspend fun unpinPost(
        @Path("id") id: String,
    ): PinPostResponse

    @POST("posts/{id}/react")
    suspend fun reactPost(
        @Path("id") id: String,
        @Body body: ReactRequest,
    ): ReactResponse

    @POST("posts/{id}/share")
    suspend fun sharePost(
        @Path("id") id: String,
    ): ShareResponse

    @GET("posts/{id}/comments")
    suspend fun listPostComments(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ListCommentsResponse

    @POST("posts/{id}/comments")
    suspend fun addPostComment(
        @Path("id") id: String,
        @Body body: CommentMutationRequest,
    ): AddCommentResponse

    @PATCH("posts/{id}/comments/{commentId}")
    suspend fun editPostComment(
        @Path("id") id: String,
        @Path("commentId") commentId: String,
        @Body body: CommentMutationRequest,
    ): MessageResponse

    @DELETE("posts/{id}/comments/{commentId}")
    suspend fun deletePostComment(
        @Path("id") id: String,
        @Path("commentId") commentId: String,
    ): MessageResponse

    @GET("blogs")
    suspend fun listBlogs(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 10,
    ): ListBlogsResponse

    @POST("blogs")
    suspend fun createBlog(
        @Body body: CreateContentRequest,
    ): CreateBlogResponse

    @GET("blogs/{id}")
    suspend fun getBlog(
        @Path("id") id: String,
        @Query("idu") idu: String? = null,
        @Query("s") signature: String? = null,
        @Query("shared") shared: String? = null,
        @Query("sid") sid: String? = null,
    ): GetBlogResponse

    @PATCH("blogs/{id}")
    suspend fun updateBlog(
        @Path("id") id: String,
        @Body body: UpdateContentRequest,
    ): MessageResponse

    @DELETE("blogs/{id}")
    suspend fun deleteBlog(
        @Path("id") id: String,
    ): MessageResponse

    @POST("blogs/{id}/react")
    suspend fun reactBlog(
        @Path("id") id: String,
        @Body body: ReactRequest,
    ): ReactResponse

    @POST("blogs/{id}/share")
    suspend fun shareBlog(
        @Path("id") id: String,
    ): ShareResponse

    @GET("blogs/{id}/comments")
    suspend fun listBlogComments(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ListCommentsResponse

    @POST("blogs/{id}/comments")
    suspend fun addBlogComment(
        @Path("id") id: String,
        @Body body: CommentMutationRequest,
    ): AddCommentResponse

    @PATCH("blogs/{id}/comments/{commentId}")
    suspend fun editBlogComment(
        @Path("id") id: String,
        @Path("commentId") commentId: String,
        @Body body: CommentMutationRequest,
    ): MessageResponse

    @DELETE("blogs/{id}/comments/{commentId}")
    suspend fun deleteBlogComment(
        @Path("id") id: String,
        @Path("commentId") commentId: String,
    ): MessageResponse
}
