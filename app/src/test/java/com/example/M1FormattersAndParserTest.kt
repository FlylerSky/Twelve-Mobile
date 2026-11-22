package com.example

import com.example.core.util.CoverCropMath
import com.example.core.util.NumberFormatters
import com.example.core.util.PostScoreCalculator
import com.example.core.util.ProfileExtrasCatalog
import com.example.core.util.SafeHtmlParser
import com.example.core.util.TagNameNormalizer
import com.example.core.util.TimeFormatter
import com.example.data.model.CoverCrop
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M1FormattersAndParserTest {

    @Test
    fun `NumberFormatters handles counts and negative scores`() {
        assertEquals("0", NumberFormatters.formatCount(0))
        assertEquals("0", NumberFormatters.formatCount(-5))
        assertEquals("999", NumberFormatters.formatCount(999))
        assertEquals("1.5K", NumberFormatters.formatCount(1500))
        assertEquals("120K", NumberFormatters.formatCount(120_400))
        assertEquals("2.3M", NumberFormatters.formatCount(2_340_000))

        assertEquals("-2", NumberFormatters.formatScore(-2))
        assertEquals("-1.5K", NumberFormatters.formatScore(-1500))
        assertEquals("0", NumberFormatters.formatScore(0))
        assertEquals("42", NumberFormatters.formatScore(42))
    }

    @Test
    fun `TimeFormatter formats relative time and profile dates`() {
        val now = Instant.parse("2026-01-10T12:00:00Z")
        assertEquals("Vài giây trước", TimeFormatter.formatRelativeTime(now.minusSeconds(20), now))
        assertEquals("5 phút trước", TimeFormatter.formatRelativeTime(now.minusSeconds(300), now))
        assertEquals("3 giờ trước", TimeFormatter.formatRelativeTime(now.minusSeconds(3 * 3600L), now))
        val old = TimeFormatter.formatRelativeTime(now.minusSeconds(48 * 3600L), now)
        assertTrue(old.contains("2026"))

        assertEquals("14/05/1999", TimeFormatter.formatBirthday("1999-05-14"))
        assertEquals("Tuần 2026-W02", TimeFormatter.formatWeekLabel("2026-W02"))
    }

    @Test
    fun `PostScoreCalculator calculates breakdown matching backend formula`() {
        val total = PostScoreCalculator.computePostScore(
            viewCount = 10,
            likeCount = 3,
            dislikeCount = 4,
            commentCount = 2,
            shareCount = 1,
        )
        // 10*1 + 3*2 + 4*(-2) + 2*3 + 1*4 = 10 + 6 - 8 + 6 + 4 = 18
        assertEquals(18, total)
    }

    @Test
    fun `CoverCropMath validates crop bounds and computes transform`() {
        assertTrue(CoverCropMath.isValidCrop(CoverCrop(x = 10.0, y = 20.0, width = 80.0, height = 60.0)))
        assertFalse(CoverCropMath.isValidCrop(CoverCrop(x = 0.0, y = 0.0, width = 5.0, height = 50.0)))
        assertFalse(CoverCropMath.isValidCrop(CoverCrop(x = 50.0, y = 0.0, width = 60.0, height = 50.0)))

        val transform = CoverCropMath.computeTransform(CoverCrop(x = 25.0, y = 10.0, width = 50.0, height = 50.0))
        assertNotNull(transform)
        assertEquals(2.0f, transform!!.widthFactor, 0.001f)
        assertEquals(-0.5f, transform.offsetXFactor, 0.001f)

        // Kiểm tra hồi quy lỗi mất ảnh bìa: khi y >= height (vd y=55, height=40),
        // heightPx phải lớn hơn containerHeightPx (2.5x) thay vì bị kẹp = 1x làm ảnh trôi khỏi khung.
        val placement = CoverCropMath.computePixelPlacement(
            containerWidthPx = 900,
            containerHeightPx = 300,
            crop = CoverCrop(x = 0.0, y = 55.0, width = 100.0, height = 40.0),
        )
        assertNotNull(placement)
        assertEquals(900, placement!!.widthPx)
        assertEquals(750, placement.heightPx)
        assertEquals(0, placement.offsetXPx)
        assertEquals(-412, placement.offsetYPx)
    }

    @Test
    fun `ProfileExtrasCatalog resolves defaults and formats rows`() {
        val resolved = ProfileExtrasCatalog.resolveExtras(mapOf("birthday" to true, "displayName" to false))
        assertEquals(true, resolved["birthday"])
        assertEquals(false, resolved["displayName"])
        assertEquals(true, resolved["accountGroup"])

        val rows = ProfileExtrasCatalog.buildExtraRows(
            mapOf(
                "accountGroup" to "SA",
                "birthday" to "2000-08-19",
                "gender" to "female",
            ),
        )
        assertEquals(3, rows.size)
        assertEquals("Tài khoản cấp cao (SA)", rows[0].value)
        assertEquals("19/08/2000", rows[1].value)
        assertEquals("Nữ", rows[2].value)
    }

    @Test
    fun `SafeHtmlParser strips scripts and dangerous URI schemes while preserving safe blocks`() {
        val dirtyHtml = """
            <script>alert('xss')</script>
            <p class="ql-align-center" onclick="steal()">Xin <strong>chào</strong> <a href="javascript:alert(1)">bẫy</a> và <a href="https://twelveta.vercel.app">trang chủ</a></p>
            <img src="http://insecure.example.com/a.jpg" alt="bad" />
            <img src="https://res.cloudinary.com/demo/image/upload/sample.jpg" alt="ok" />
            <iframe src="https://www.youtube.com/embed/abc123"></iframe>
            <ol><li data-list="checked">Mục 1</li></ol>
        """.trimIndent()

        val blocks = SafeHtmlParser.parse(dirtyHtml)
        assertFalse(blocks.any { it.toString().contains("alert") || it.toString().contains("javascript:") })

        val paragraph = blocks.filterIsInstance<SafeHtmlParser.HtmlBlock.Paragraph>().first()
        assertEquals(SafeHtmlParser.TextAlign.CENTER, paragraph.align)
        val links = paragraph.spans.filterIsInstance<SafeHtmlParser.InlineSpan.Link>()
        assertEquals(1, links.size)
        assertEquals("https://twelveta.vercel.app", links.first().url)

        val images = blocks.filterIsInstance<SafeHtmlParser.HtmlBlock.Image>()
        assertEquals(1, images.size)
        assertEquals("https://res.cloudinary.com/demo/image/upload/sample.jpg", images.first().url)

        val embeds = blocks.filterIsInstance<SafeHtmlParser.HtmlBlock.EmbedCard>()
        assertEquals(1, embeds.size)
        assertEquals(SafeHtmlParser.EmbedKind.IFRAME, embeds.first().kind)

        val listItem = blocks.filterIsInstance<SafeHtmlParser.HtmlBlock.BulletListItem>().first()
        assertEquals("[x]", listItem.marker)

        assertNull(SafeHtmlParser.isSafeWebLink("intent://scan/#Intent"))
        assertNull(SafeHtmlParser.isSafeWebLink("file:///etc/passwd"))
        assertNull(SafeHtmlParser.isSafeWebLink("content://media/external/1"))
        assertNull(SafeHtmlParser.isSafeHttpsMedia("data:image/png;base64,AAAA"))
    }

    @Test
    fun `TagNameNormalizer builds canonical web profile paths`() {
        assertEquals("/u/@flylersky20", TagNameNormalizer.buildWebProfilePath("@FlylerSky20", "uid123"))
        assertEquals("/u/@alice_1", TagNameNormalizer.buildWebProfilePath("alice_1", "uid123"))
        assertEquals("/u/uid123", TagNameNormalizer.buildWebProfilePath(null, "uid123"))
        assertEquals("/u/uid123", TagNameNormalizer.buildWebProfilePath("invalid-tag!", "uid123"))
    }
}
