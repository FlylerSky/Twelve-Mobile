package com.example

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.example.core.util.DeepLinkParser
import com.example.core.util.TwelveDeepLink
import com.example.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class M4DeepLinksAndHardeningTest {

    @Test
    fun `DeepLinkParser parses valid post, blog and profile deep links including share tracking`() {
        val postLink = DeepLinkParser.parse(
            "https://twelveta.vercel.app/post/post_123?idu=sharer_9&s=sig_abc-12=&shared=1",
        ) as? TwelveDeepLink.Content
        assertNotNull(postLink)
        assertEquals(ContentType.POST, postLink?.contentType)
        assertEquals("post_123", postLink?.id)
        assertEquals("sharer_9", postLink?.sharerUid)
        assertEquals("sig_abc-12=", postLink?.shareSignature)
        assertTrue(postLink?.sharedFlag == true)

        val blogLink = DeepLinkParser.parse(
            "https://TWELVETA.VERCEL.APP/blog/blog_01/?shared=1",
        ) as? TwelveDeepLink.Content
        assertNotNull(blogLink)
        assertEquals(ContentType.BLOG, blogLink?.contentType)
        assertEquals("blog_01", blogLink?.id)
        assertNull(blogLink?.sharerUid)
        assertNull(blogLink?.shareSignature)
        assertTrue(blogLink?.sharedFlag == true)

        val tagLink = DeepLinkParser.parse(
            "https://twelveta.vercel.app/u/@Minh_Anh12",
        ) as? TwelveDeepLink.PublicProfile
        assertEquals(TwelveDeepLink.PublicProfile("minh_anh12", isTag = true), tagLink)

        val selfLink = DeepLinkParser.parse("https://twelveta.vercel.app/u/myProfile")
        assertEquals(TwelveDeepLink.OwnProfile, selfLink)

        val uidLink = DeepLinkParser.parse(
            "https://twelveta.vercel.app/u/abcDEF1234567890",
        ) as? TwelveDeepLink.PublicProfile
        assertEquals(TwelveDeepLink.PublicProfile("abcDEF1234567890", isTag = false), uidLink)
    }

    @Test
    fun `DeepLinkParser rejects untrusted schemes, hosts, path traversal, reserved doc IDs and invalid tags`() {
        assertNull(DeepLinkParser.parse("http://twelveta.vercel.app/post/p1"))
        assertNull(DeepLinkParser.parse("https://evil.example.com/post/p1"))
        assertNull(DeepLinkParser.parse("javascript:alert(1)"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/post/../users/me"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/post/a/b"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/post/__reserved__"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/u/@ab"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/u/@bad-tag"))
        assertNull(DeepLinkParser.parse("https://twelveta.vercel.app/trending"))

        // Query params độc hại ở share signature bị loại bỏ thay vì lọt xuống backend
        val sanitized = DeepLinkParser.parse(
            "https://twelveta.vercel.app/post/p1?idu=u1&s=bad%20sig%21",
        ) as? TwelveDeepLink.Content
        assertNotNull(sanitized)
        assertNull(sanitized?.sharerUid)
        assertNull(sanitized?.shareSignature)
    }

    @Test
    fun `Manifest hardening disables backup, omits broad storage permissions, and resolves deep link intents`() {
        val app = RuntimeEnvironment.getApplication()
        val pm = app.packageManager
        val pkgInfo = pm.getPackageInfo(
            app.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_ACTIVITIES,
        )
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)

        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue("android.permission.INTERNET" in requested)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in requested)
        assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in requested)
        assertFalse("android.permission.READ_MEDIA_IMAGES" in requested)
        assertFalse("android.permission.READ_MEDIA_VIDEO" in requested)

        // Khả năng giải quyết intent-filter App Links tới đúng MainActivity
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twelveta.vercel.app/post/p99"))
            .setPackage(app.packageName)
        val resolved = pm.resolveActivity(viewIntent, 0)
        assertNotNull(resolved)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }

    @Test
    fun `MainActivity launches safely on deep link intent without google-services json`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twelveta.vercel.app/u/@alice_12"))
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
