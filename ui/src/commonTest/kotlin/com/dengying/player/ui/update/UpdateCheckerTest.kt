package com.dengying.player.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UpdateCheckerTest {

    private val releaseJson = """
        {
          "tag_name": "v1.3.14",
          "published_at": "2026-11-18T10:20:30Z",
          "body": "## 修复\n- 修复播放进度\n- 优化搜索\n- 新增更新提示\n- 第四条不会显示",
          "html_url": "https://github.com/xiao-hans/DengYing/releases/tag/v1.3.14"
        }
    """.trimIndent()

    private val giteeReleaseJson = """
        {
          "tag_name": "v1.3.14",
          "created_at": "2026/11/18 22:31:23",
          "body": "## 修复\n- 修复播放进度\n- 优化搜索\n- 新增更新提示\n- 第四条不会显示"
        }
    """.trimIndent()

    @Test
    fun `newer release is available`() {
        val result = UpdateChecker.parseLatest(releaseJson, "1.3.13")
        val info = (result as UpdateCheckResult.Available).info

        assertEquals("v1.3.14", info.tag)
        assertEquals("1.3.14", info.version)
        assertEquals("2026-11-18", info.publishedDate)
        assertEquals(
            "https://github.com/xiao-hans/DengYing/releases/tag/v1.3.14",
            info.url,
        )
        assertEquals(3, info.notes.lines().size)
        assertTrue(info.notes.endsWith("…"))
    }

    @Test
    fun `same or older release is up to date`() {
        assertSame(UpdateCheckResult.UpToDate, UpdateChecker.parseLatest(releaseJson, "1.3.14"))
        assertSame(UpdateCheckResult.UpToDate, UpdateChecker.parseLatest(releaseJson, "1.3.15"))
    }

    @Test
    fun `gitee newer release is available`() {
        val result = UpdateChecker.parseGiteeLatest(giteeReleaseJson, "1.3.13")
        val info = (result as UpdateCheckResult.Available).info

        assertEquals("v1.3.14", info.tag)
        assertEquals("1.3.14", info.version)
        assertEquals("2026-11-18", info.publishedDate)
        assertEquals(
            "https://gitee.com/han-xiaos-whisper/DengYing/releases/tag/v1.3.14",
            info.url,
        )
        assertEquals(3, info.notes.lines().size)
        assertTrue(info.notes.endsWith("…"))
        assertSame(UpdateCheckResult.UpToDate, UpdateChecker.parseGiteeLatest(giteeReleaseJson, "1.3.14"))
    }

    @Test
    fun `gitee release tag is restricted to a safe path segment`() {
        assertSame(
            UpdateCheckResult.Failed,
            UpdateChecker.parseGiteeLatest(
                giteeReleaseJson.replace("\"v1.3.14\"", "\"v1.3.14/../evil\""),
                "1.3.13",
            ),
        )
        assertSame(
            UpdateCheckResult.Failed,
            UpdateChecker.parseGiteeLatest(
                giteeReleaseJson.replace("\"v1.3.14\"", "\".v1.3.14\""),
                "1.3.13",
            ),
        )
    }

    @Test
    fun `version comparison is numeric not lexical`() {
        assertTrue(UpdateChecker.parseVersion("1.3.10")!! > UpdateChecker.parseVersion("1.3.9")!!)
        assertTrue(UpdateChecker.parseVersion("v2.0.0")!! > UpdateChecker.parseVersion("1.99.99")!!)
    }

    @Test
    fun `missing local version disables check`() {
        assertSame(
            UpdateCheckResult.Disabled,
            UpdateChecker.parseLatest(releaseJson, ""),
        )
        assertSame(
            UpdateCheckResult.Disabled,
            UpdateChecker.parseLatest(releaseJson, "0.0.0-dev"),
        )
    }

    @Test
    fun `invalid release fields fail closed`() {
        assertSame(UpdateCheckResult.Failed, UpdateChecker.parseLatest("not-json", "1.3.13"))
        assertSame(
            UpdateCheckResult.Failed,
            UpdateChecker.parseLatest(releaseJson.replace("\"v1.3.14\"", "\"latest\""), "1.3.13"),
        )
        assertSame(
            UpdateCheckResult.Failed,
            UpdateChecker.parseLatest(
                releaseJson.replace(
                    "https://github.com/xiao-hans/DengYing/releases/tag/v1.3.14",
                    "https://example.com/download",
                ),
                "1.3.13",
            ),
        )
    }

    @Test
    fun `release url only accepts this repository over https`() {
        assertTrue(
            UpdateChecker.isAllowedReleaseUrl(
                "https://github.com/xiao-hans/DengYing/releases/tag/v1.3.14",
            ),
        )
        assertTrue(
            UpdateChecker.isAllowedReleaseUrl(
                "https://gitee.com/han-xiaos-whisper/DengYing/releases/tag/v1.3.14",
            ),
        )
        assertTrue(
            UpdateChecker.isAllowedReleaseUrl(
                "https://gitee.com/han-xiaos-whisper/DengYing/releases/" +
                    "download/v1.3.14/app-release.apk",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "http://github.com/xiao-hans/DengYing/releases/tag/v1.3.14",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "https://github.com.evil.example/xiao-hans/DengYing/releases",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "https://github.com/xiao-hans/Other/releases",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "https://gitee.com/han-xiaos-whisper/Other/releases",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "https://gitee.com.evil.example/han-xiaos-whisper/DengYing/releases",
            ),
        )
    }

    private fun available(version: String) = UpdateCheckResult.Available(
        UpdateInfo(
            tag = "v$version",
            version = version,
            publishedDate = "",
            notes = "",
            url = "https://gitee.com/han-xiaos-whisper/DengYing/releases/tag/v$version",
        ),
    )

    @Test
    fun `newer of the two sources wins`() {
        val giteeOlder = available("1.3.14")
        val githubNewer = available("1.3.15")

        assertSame(githubNewer, UpdateChecker.pickNewer(giteeOlder, githubNewer))
        assertSame(githubNewer, UpdateChecker.pickNewer(githubNewer, giteeOlder))
    }

    @Test
    fun `lagging source does not hide the newer release`() {
        val newer = available("1.3.15")

        assertSame(newer, UpdateChecker.pickNewer(UpdateCheckResult.UpToDate, newer))
        assertSame(newer, UpdateChecker.pickNewer(newer, UpdateCheckResult.Failed))
    }

    @Test
    fun `both sources failing stays failed`() {
        assertEquals(
            UpdateCheckResult.UpToDate,
            UpdateChecker.pickNewer(UpdateCheckResult.UpToDate, UpdateCheckResult.Failed),
        )
        assertEquals(
            UpdateCheckResult.Failed,
            UpdateChecker.pickNewer(UpdateCheckResult.Failed, UpdateCheckResult.Failed),
        )
    }

    @Test
    fun `notes are plain text and capped`() {
        assertEquals("", UpdateChecker.formatNotes(""))
        assertEquals(
            "第一行\n第二行\n第三行…",
            UpdateChecker.formatNotes(
                "## 第一行\n> 第二行\n- **第三行**\n- 第四行",
            ),
        )
    }
}
