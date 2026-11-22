package com.dengying.player.ui.update

import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * 应用内更新检查。
 *
 * 只访问固定仓库的 `releases/latest`，复用项目唯一的 [HttpClient]。不引入
 * 更新 SDK，也不接受用户填写的检查地址：源固定为编译期常量，避免把任意 URL
 * 变成第二套下载/更新通道。
 *
 * Gitee 是境内主检查源；Gitee 不可用时回退到 GitHub。两者都只能指向本仓库的
 * Releases 页面，避免 API 返回值把用户带到任意站点。
 *
 * 解析与比较拆成纯函数，JVM 测试不需要打真实 GitHub。
 */
object UpdateChecker {

    const val GITHUB_RELEASES_LATEST_URL =
        "https://api.github.com/repos/xiao-hans/DengYing/releases/latest"

    const val GITEE_RELEASES_LATEST_URL =
        "https://gitee.com/api/v5/repos/han-xiaos-whisper/DengYing/releases/latest"

    private const val ACCEPT = "Accept"
    private const val GITHUB_ACCEPT = "application/vnd.github+json"
    private const val JSON_ACCEPT = "application/json"
    private const val GITHUB_RELEASES_PREFIX = "https://github.com/xiao-hans/DengYing/releases"
    private const val GITEE_RELEASES_PREFIX =
        "https://gitee.com/han-xiaos-whisper/DengYing/releases"
    private val RELEASE_TAG_RE = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,63}""")
    private val VERSION_RE = Regex("""(\d+)\.(\d+)\.(\d+)""")
    private val HTML_TAG_RE = Regex("<[^>]*>")

    /**
     * GitHub Release 的 APK 直链。
     *
     * 【为什么 Gitee 版本要绕到 GitHub 下文件】Gitee 对 `.apk` 附件返回
     * `Content-Type: application/zip`，手机浏览器据此把文件存成
     * `app-release.apk.zip`；GitHub 返回 `application/vnd.android.package-archive`，
     * 浏览器会保留 `.apk` 后缀。Gitee 的 MIME 映射在服务端，客户端改不了，
     * 所以下载入口默认走 GitHub 直链，Gitee 页面只作为镜像/备用说明。
     */
    internal fun githubApkUrl(version: String): String? {
        if (!VERSION_RE.matches(version)) return null
        return "$GITHUB_RELEASES_PREFIX/download/v$version/app-release.apk"
    }

    internal data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val majorDiff = major.compareTo(other.major)
            if (majorDiff != 0) return majorDiff
            val minorDiff = minor.compareTo(other.minor)
            if (minorDiff != 0) return minorDiff
            return patch.compareTo(other.patch)
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    /** 从 `v1.3.14` / `1.3.14-beta` 等文本中取三段版本号；取不到返回 null。 */
    internal fun parseVersion(raw: String): SemVer? {
        val match = VERSION_RE.find(raw.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        return SemVer(
            major = major.toIntOrNull() ?: return null,
            minor = minor.toIntOrNull() ?: return null,
            patch = patch.toIntOrNull() ?: return null,
        )
    }

    /**
     * 可参与检查的当前版本。
     *
     * `0.0.0` 是「平台壳没有注入真实版本」的常见占位值；把它当作有效版本会让
     * 任何 release 都显得更新，因此与空串一样禁用自动检查。
     */
    internal fun parseCheckableVersion(raw: String): SemVer? =
        parseVersion(raw)?.takeIf { it.major != 0 || it.minor != 0 || it.patch != 0 }

    /** 只接受本仓库 GitHub / Gitee Releases 的 HTTPS 页面。 */
    internal fun isAllowedReleaseUrl(url: String): Boolean {
        if (url.any { it.isWhitespace() || it.code < 0x20 }) return false
        return listOf(GITHUB_RELEASES_PREFIX, GITEE_RELEASES_PREFIX).any { prefix ->
            url == prefix || url.startsWith("$prefix/") || url.startsWith("$prefix?")
        }
    }

    /**
     * 把 release 正文压成最多三行纯文本。
     *
     * GitHub release body 是 Markdown；这里不渲染 HTML/Markdown，只保留可读文本。
     * 超出的行用省略号提示，避免弹窗被长更新日志撑开。
     */
    internal fun formatNotes(body: String): String {
        val lines = body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { line ->
                line
                    .replace(HTML_TAG_RE, "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .trim()
                    .trimStart('#', '>', '-', '*', ' ')
                    .trim()
            }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        val shown = lines.take(3)
        if (lines.size > 3) {
            return shown.dropLast(1).plus("${shown.last()}…").joinToString("\n")
        }
        return shown.joinToString("\n")
    }

    /** 把 `published_at` / `created_at` 压成 `yyyy-MM-dd`；无效值返回空串。 */
    internal fun publishedDate(raw: String): String {
        val match = Regex("""(\d{4})[-/](\d{2})[-/](\d{2})""").find(raw.trim()) ?: return ""
        val (year, month, day) = match.destructured
        return "$year-$month-$day"
    }

    /**
     * 解析 GitHub JSON；纯函数，测试可直接喂字符串。
     *
     * 当前版本无法解析时返回 [UpdateCheckResult.Disabled]，避免在版本号缺失的
     * 平台（尚未注入版本信息的壳）误报更新。
     */
    internal fun parseLatest(text: String, currentVersion: String): UpdateCheckResult {
        val current = parseCheckableVersion(currentVersion) ?: return UpdateCheckResult.Disabled
        val json = HttpClient.safeJson(text) ?: return UpdateCheckResult.Failed
        return parseGithubRelease(json, current)
    }

    /** 解析 Gitee JSON；字段名与 GitHub 略有差异，释放源仍固定。 */
    internal fun parseGiteeLatest(text: String, currentVersion: String): UpdateCheckResult {
        val current = parseCheckableVersion(currentVersion) ?: return UpdateCheckResult.Disabled
        val json = HttpClient.safeJson(text) ?: return UpdateCheckResult.Failed
        return parseGiteeRelease(json, current)
    }

    internal fun parseGithubRelease(json: JsonObject, current: SemVer): UpdateCheckResult {
        val tag = JsonX.str(json, "tag_name").trim()
        val url = JsonX.str(json, "html_url").trim()
        return releaseResult(
            tag = tag,
            publishedAt = JsonX.str(json, "published_at"),
            body = JsonX.str(json, "body"),
            url = url,
            current = current,
        )
    }

    internal fun parseGiteeRelease(json: JsonObject, current: SemVer): UpdateCheckResult {
        val tag = JsonX.str(json, "tag_name").trim()
        if (!RELEASE_TAG_RE.matches(tag)) return UpdateCheckResult.Failed
        return releaseResult(
            tag = tag,
            publishedAt = JsonX.str(json, "created_at"),
            body = JsonX.str(json, "body"),
            url = "$GITEE_RELEASES_PREFIX/tag/$tag",
            current = current,
        )
    }

    private fun releaseResult(
        tag: String,
        publishedAt: String,
        body: String,
        url: String,
        current: SemVer,
    ): UpdateCheckResult {
        val latest = parseVersion(tag) ?: return UpdateCheckResult.Failed
        if (latest <= current) return UpdateCheckResult.UpToDate
        if (!isAllowedReleaseUrl(url)) return UpdateCheckResult.Failed

        return UpdateCheckResult.Available(
            UpdateInfo(
                tag = tag,
                version = latest.toString(),
                publishedDate = publishedDate(publishedAt),
                notes = formatNotes(body),
                url = url,
            ),
        )
    }

    /**
     * 同时请求 Gitee 与 GitHub，取两者中更新的版本。
     *
     * 【为什么不按「Gitee 失败才回退」】任一源都可能发布滞后，只在请求失败时
     * 回退会把落后源的旧 tag 当成最新版本，用户就永远看不到另一个源上的新版。
     * 两个请求并行发起，任一成功即可给出结果，两个都失败才返回 Failed。
     *
     * 网络异常/非 200 都收敛成 [UpdateCheckResult.Failed]，由调用方决定是否
     * 记录检查时间；这里不抛出，避免一次更新检查影响启动流程。
     */
    suspend fun fetchLatest(currentVersion: String): UpdateCheckResult {
        if (parseCheckableVersion(currentVersion) == null) return UpdateCheckResult.Disabled
        return try {
            withContext(ioDispatcher) {
                coroutineScope {
                    val gitee = async { fetchGitee(currentVersion) }
                    val github = async { fetchGithub(currentVersion) }
                    pickNewer(gitee.await(), github.await())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UpdateCheckResult.Failed
        }
    }

    /**
     * 从两个源的结果里挑更新的一个。
     *
     * [UpdateCheckResult.Available] 一定高于当前版本（见 [releaseResult]），
     * 所以两个 Available 只需比较版本号；只有一个成功时直接用它的结论。
     */
    internal fun pickNewer(first: UpdateCheckResult, second: UpdateCheckResult): UpdateCheckResult {
        val firstVersion = (first as? UpdateCheckResult.Available)?.let { parseVersion(it.info.version) }
        val secondVersion = (second as? UpdateCheckResult.Available)?.let { parseVersion(it.info.version) }
        return when {
            firstVersion != null && secondVersion != null ->
                if (firstVersion >= secondVersion) first else second
            firstVersion != null -> first
            secondVersion != null -> second
            first == UpdateCheckResult.UpToDate || second == UpdateCheckResult.UpToDate ->
                UpdateCheckResult.UpToDate
            first == UpdateCheckResult.Disabled && second == UpdateCheckResult.Disabled ->
                UpdateCheckResult.Disabled
            else -> UpdateCheckResult.Failed
        }
    }

    private suspend fun fetchGitee(currentVersion: String): UpdateCheckResult = fetch(
        url = GITEE_RELEASES_LATEST_URL,
        accept = JSON_ACCEPT,
    ) { body -> parseGiteeLatest(body, currentVersion) }

    private suspend fun fetchGithub(currentVersion: String): UpdateCheckResult = fetch(
        url = GITHUB_RELEASES_LATEST_URL,
        accept = GITHUB_ACCEPT,
    ) { body -> parseLatest(body, currentVersion) }

    private suspend fun fetch(
        url: String,
        accept: String,
        parse: (String) -> UpdateCheckResult,
    ): UpdateCheckResult {
        val result = HttpClient.get(url, headers = mapOf(ACCEPT to accept))
        return if (result.status != 200) UpdateCheckResult.Failed else parse(result.body)
    }
}
