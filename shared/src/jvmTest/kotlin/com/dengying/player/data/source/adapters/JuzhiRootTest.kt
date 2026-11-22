package com.dengying.player.data.source.adapters

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 桔子根域下发解析（`JuzhiRoot.parseRoss`）
 *
 * 只测**纯解析**：网络分支（超时/非 200）不可复现，而「什么形状算可用」是纯字符串判定，
 * 用真实下发样本 `juzhi_ross.txt` 加若干负向样本就能钉死。
 *
 * 真实样本（2026-10 实测，HTTP 200 / 98B）：
 * ```
 * {"domain": "http://fuckducai.biosyne.cn",
 * "enabled": true,
 * "offlineUrl":"https://juzhi7777.app"}
 * ```
 * 注意它**跨行**且 domain 值前后各有空格 —— 这两点都必须被正确处理，
 * 否则换域后整个源会静默回退到已 502 的写死地址。
 */
class JuzhiRootTest {

    // ---------------- 正向：真实样本 ----------------

    @Test
    fun realSampleYieldsDomain() {
        val body = Golden.text("juzhi_ross")
        assertEquals("http://fuckducai.biosyne.cn", JuzhiRoot.parseRoss(body))
    }

    @Test
    fun realSampleIsNotTheFallback() {
        // 回归守卫：曾经写死过 http://103.36.167.27:18004（已 502）。
        // 若有人把兜底值写回 domain 字段，这条会红。
        val body = Golden.text("juzhi_ross")
        assertEquals(false, JuzhiRoot.parseRoss(body).contains("103.36.167.27"))
    }

    // ---------------- 正向：形态边界 ----------------

    @Test
    fun trailingSlashIsStripped() {
        assertEquals(
            "http://a.example",
            JuzhiRoot.parseRoss("""{"domain":"http://a.example/","enabled":true}"""),
        )
    }

    @Test
    fun multipleTrailingSlashesAreStripped() {
        // trimEnd('/') 是剥掉全部尾斜杠，不是只剥一个 —— 调用方拼 "$base/api/..."
        assertEquals(
            "https://a.example",
            JuzhiRoot.parseRoss("""{"domain":"https://a.example///","enabled":true}"""),
        )
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(
            "https://a.example",
            JuzhiRoot.parseRoss("""{"domain":"  https://a.example  ","enabled":true}"""),
        )
    }

    @Test
    fun missingEnabledDefaultsToTrue() {
        // enabled 缺省按可用处理：核心字段是 domain，缺 enabled 不该判死
        assertEquals(
            "https://a.example",
            JuzhiRoot.parseRoss("""{"domain":"https://a.example"}"""),
        )
    }

    @Test
    fun httpSchemeIsAccepted() {
        // 明文 http 也接受：桔子的下发域实测就是 http（ATS 例外已在清单里）
        assertEquals(
            "http://a.example",
            JuzhiRoot.parseRoss("""{"domain":"http://a.example","enabled":true}"""),
        )
    }

    @Test
    fun extraFieldsAreIgnored() {
        // offlineUrl 等字段不参与判定
        assertEquals(
            "https://a.example",
            JuzhiRoot.parseRoss(
                """{"domain":"https://a.example","enabled":true,"offlineUrl":"https://x.example"}""",
            ),
        )
    }

    // ---------------- 负向：enabled=false ----------------

    @Test
    fun enabledFalseYieldsEmpty() {
        // 服务端主动下线：不采用下发域，回退默认
        assertEquals(
            "",
            JuzhiRoot.parseRoss("""{"domain":"https://a.example","enabled":false}"""),
        )
    }

    @Test
    fun enabledStringFalseYieldsEmpty() {
        // org.json 语义：字符串 "false" 转布尔为 false（JsonX.bool 逐字对齐）
        assertEquals(
            "",
            JuzhiRoot.parseRoss("""{"domain":"https://a.example","enabled":"false"}"""),
        )
    }

    // ---------------- 负向：domain 非法 ----------------

    @Test
    fun missingDomainYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss("""{"enabled":true}"""))
    }

    @Test
    fun emptyDomainYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":"","enabled":true}"""))
    }

    @Test
    fun whitespaceOnlyDomainYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":"   ","enabled":true}"""))
    }

    @Test
    fun nonHttpDomainYieldsEmpty() {
        // 协议相对写法 //host 与相对路径 /api 都不接受
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":"//a.example","enabled":true}"""))
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":"/api","enabled":true}"""))
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":"a.example","enabled":true}"""))
    }

    @Test
    fun domainWithOnlySchemeAndSlashesYieldsHttpPrefix() {
        // 边界：剥完尾斜杠后剩下 "http:" —— 仍以 http 开头，故被接受。
        // 这不是理想值，但服务端下发合法域时不会出现；记录实际行为以免误以为有校验。
        assertEquals("http:", JuzhiRoot.parseRoss("""{"domain":"http://","enabled":true}"""))
    }

    // ---------------- 负向：整体形态 ----------------

    @Test
    fun emptyBodyYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss(""))
    }

    @Test
    fun plainTextYieldsEmpty() {
        // 同目录的 oss.txt 是 "0903-1030" 形态的日期区间，不是接口 —— 不能误当根域
        assertEquals("", JuzhiRoot.parseRoss("0903-1030"))
    }

    @Test
    fun jsonArrayYieldsEmpty() {
        // parseObject 只收顶层对象
        assertEquals("", JuzhiRoot.parseRoss("""[{"domain":"https://a.example"}]"""))
    }

    @Test
    fun htmlErrorPageYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss("<html><body>502 Bad Gateway</body></html>"))
    }

    @Test
    fun brokenJsonYieldsEmpty() {
        assertEquals("", JuzhiRoot.parseRoss("""{"domain": "https://a.example","""))
    }

    @Test
    fun domainAsNumberYieldsEmpty() {
        // JsonX.str 会把数字转成文本 "123"，不以 http 开头 → 空串
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":123,"enabled":true}"""))
    }

    @Test
    fun domainAsNullYieldsEmpty() {
        // JsonNull → fallback ""（P3 批 C-5 已把 textOf 的 JsonNull 分支修为返回 fallback）
        assertEquals("", JuzhiRoot.parseRoss("""{"domain":null,"enabled":true}"""))
    }
}
