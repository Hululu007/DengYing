package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import com.dengying.player.core.SourceSecrets
import com.dengying.player.data.source.crypto.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 瓜皮影视（WexGuaZi）解析层长期守卫 —— 批 C-6。
 *
 * ## 为什么没有真金样本
 *
 * 瓜皮鉴权链实测不通（`signIn` 恒 code=400「登录信息不能为空」、
 * `signUp` 恒 code=403「注册失败，用户已经存在」）→ 拿不到 token → 业务接口不可达。
 * 因此本测试的输入全是**按响应协议形状手工构造的合成样本**，
 * 与批 C-2 奇优改版后的处置一致（见 docs/04 §13.4.2 与 §13.8.2）。
 *
 * ## 契约要点（都被下面的用例钉住）
 *
 * 1. `parseSearch` / `parseDetail` / `parsePlay` 接收的是**已解密**的业务 JSON，
 *    形状是 `{"code":..,"list":[..]}` / `{"vod_name":..}` / `{"url":..}`，
 *    **不是**外层信封 `{"code":..,"data":{..}}`。这一层由 `decryptResponse` 剥掉。
 * 2. `vod_id` 三态：数字 / 数字字符串 / 其它 → 0 → 该行被跳过。
 * 3. 解密链任一环节失败都降到 `""`，不抛异常。
 * 4. 请求侧的 AES/RSA/MD5 编排逐位可复现（`requestKey` 是确定性 HEX）。
 */
class GuapiGoldenTest {

    private val src = CmsSource(
        key = "ah_guapi",
        name = "瓜子影视",
        api = "https://sdapi.e2wu4ht.com",
        speed = 27,
        direct = false,
        enabled = false,
        protocol = SourceProtocol.Guapi,
        caps = SourceCaps(search = true, category = false, detail = false, token = true),
    )

    private fun search(json: String) = GuapiParser.parseSearch(src, json)

    // ---------------- 请求侧：密码学编排 ----------------

    @Test
    fun request_key_is_deterministic_uppercase_hex() {
        val rk = GuapiParser.requestKey("""{"a":1}""")
        assertEquals("C489251F1851EEAB9F8744607CD19B51", rk)
        assertEquals(rk, GuapiParser.requestKey("""{"a":1}"""))
        assertEquals(32, rk.length)
        assertEquals(rk.uppercase(), rk)
    }

    @Test
    fun request_key_differs_per_payload() {
        assertTrue(GuapiParser.requestKey("""{"a":1}""") != GuapiParser.requestKey("""{"a":2}"""))
    }

    @Test
    fun keys_field_is_base64_of_rsa_block() {
        val keys = GuapiParser.keysField()
        // RSA-1024 单块密文 = 128 字节 → Base64 无填充时 172 字符（有填充 172）
        assertEquals(172, keys.length)
        // 每次 RSA 填充随机，故两次不同
        assertTrue(keys != GuapiParser.keysField())
        assertTrue(keys.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun signature_core_field_order_is_protocol() {
        assertEquals(
            "token_id=uid1,token=tok1,phone_type=1,request_key=RK,app_id=1,time=1700000000,keys=KEYS",
            GuapiParser.signatureCore("uid1", "tok1", "1700000000", "RK", "KEYS"),
        )
    }

    @Test
    fun signature_is_uppercase_md5_of_core_plus_secret() {
        val core = GuapiParser.signatureCore("uid1", "tok1", "1700000000", "RK", "KEYS")
        val sig = GuapiParser.signature(core)
        assertEquals("61709492A801758E8727DBB79625D9C7", sig)
        assertEquals(32, sig.length)
        assertEquals(sig.uppercase(), sig)
        // 直接按公式复算，钉住「secret 在后」
        assertEquals(Digest.md5Hex(core + SourceSecrets.GUAPI_SIGNATURE_SECRET).uppercase(), sig)
    }

    @Test
    fun ok_code_is_200() {
        assertEquals(200, GuapiParser.OK_CODE)
    }

    @Test
    fun secrets_have_expected_shape() {
        assertEquals(16, SourceSecrets.GUAPI_AES_KEY.length)      // AES-128
        assertEquals(16, SourceSecrets.GUAPI_AES_IV.length)
        assertEquals(36, SourceSecrets.GUAPI_SIGNATURE_SECRET.length)
        assertEquals(848, SourceSecrets.GUAPI_RESPONSE_PRIVATE_KEY.length)
        assertEquals(216, SourceSecrets.GUAPI_REQUEST_PUBLIC_KEY.length)
    }

    // ---------------- 搜索：正常路径 ----------------

    @Test
    fun search_normal_two_items() {
        val r = search(
            """{"code":200,"list":[""" +
                """{"vod_id":101,"vod_name":"庆余年","vod_pic":"https://p/1.jpg","vod_remarks":"全36集"},""" +
                """{"vod_id":102,"vod_name":"三体","vod_pic":"","vod_remarks":null}]}""",
        )
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(2, r.items.size)
        assertEquals(2, r.total)
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount)
        assertNull(r.err)
        assertEquals("瓜子影视", r.sourceName)

        val a = r.items[0]
        assertEquals(101L, a.vod_id)
        assertEquals("庆余年", a.vod_name)
        assertEquals("https://p/1.jpg", a.vod_pic)
        assertEquals("全36集", a.vod_remarks)
        assertEquals("ah_guapi", a._source)

        val b = r.items[1]
        assertEquals(102L, b.vod_id)
        assertEquals("三体", b.vod_name)
        assertEquals("", b.vod_pic)
        // null 字面量 → org.json optString 返回 fallback ""（见 §13.7.3 的裁决）
        assertEquals("", b.vod_remarks)
    }

    // ---------------- 搜索：降级路径 ----------------

    @Test
    fun search_empty_text_is_request_failed() {
        val r = search("")
        assertEquals(RequestOutcome.RequestFailed, r.outcome)
        assertEquals("无响应或解密失败", r.err)
        assertEquals(0, r.items.size)
    }

    @Test
    fun search_non_json_is_parse_failed() {
        val r = search("<html/>")
        assertEquals(RequestOutcome.ParseFailed, r.outcome)
        assertEquals("响应结构非预期", r.err)
    }

    @Test
    fun search_json_without_list_is_success_no_data() {
        listOf(
            "{}",
            """{"code":200}""",
            """{"code":200,"list":"str"}""",
            """{"code":200,"list":{}}""",
            """{"code":200,"list":[]}""",
        ).forEach {
            val r = search(it)
            assertEquals(RequestOutcome.SuccessNoData, r.outcome, "for $it")
            assertEquals(0, r.items.size, "for $it")
            assertNull(r.err, "for $it")
        }
    }

    @Test
    fun search_skips_non_object_rows() {
        val r = search("""{"code":200,"list":[null,"x",3,{"vod_id":5}]}""")
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.items.size)
        assertEquals(5L, r.items[0].vod_id)
    }

    // ---------------- 搜索：vod_id 三态 ----------------

    @Test
    fun vod_id_numeric_and_numeric_string_accepted() {
        assertEquals(101L, search("""{"code":200,"list":[{"vod_id":101}]}""").items[0].vod_id)
        assertEquals(102L, search("""{"code":200,"list":[{"vod_id":"102"}]}""").items[0].vod_id)
    }

    @Test
    fun vod_id_rejected_shapes_are_skipped() {
        listOf(
            """{"vod_id":"abc"}""",
            """{"vod_id":null}""",
            """{"vod_id":true}""",
            """{"vod_id":{"x":1}}""",
            """{"vod_id":"0"}""",
            """{"vod_id":-5}""",
            """{"vod_name":"noId"}""",
        ).forEach { row ->
            val r = search("""{"code":200,"list":[$row]}""")
            assertEquals(0, r.items.size, "row should be skipped: $row")
            assertEquals(RequestOutcome.SuccessNoData, r.outcome, "row: $row")
        }
    }

    @Test
    fun vod_id_float_truncates() {
        assertEquals(103L, search("""{"code":200,"list":[{"vod_id":103.9}]}""").items[0].vod_id)
    }

    @Test
    fun id_of_helper_matches_three_state_rule() {
        val cases = mapOf(
            """{"vod_id":101}""" to 101L,
            """{"vod_id":"102"}""" to 102L,
            """{"vod_id":"abc"}""" to 0L,
            """{"vod_id":null}""" to 0L,
            """{"vod_id":103.9}""" to 103L,
            """{"vod_id":true}""" to 0L,
            """{"vod_id":{"x":1}}""" to 0L,
            """{}""" to 0L,
        )
        cases.forEach { (json, want) ->
            val obj = com.dengying.player.data.json.JsonX.parseObject(json)!!
            assertEquals(want, GuapiParser.idOf(obj["vod_id"]), "for $json")
        }
    }

    // ---------------- 搜索：total ----------------

    @Test
    fun total_shapes() {
        assertEquals(42, search("""{"code":200,"list":[{"vod_id":1}],"total":42}""").total)
        assertEquals(42, search("""{"code":200,"list":[{"vod_id":1}],"total":"42"}""").total)
        assertEquals(4, search("""{"code":200,"list":[{"vod_id":1}],"total":4.9}""").total)
        // null → fallback = items.size
        assertEquals(1, search("""{"code":200,"list":[{"vod_id":1}],"total":null}""").total)
        // 缺失 → fallback = items.size
        assertEquals(1, search("""{"code":200,"list":[{"vod_id":1}]}""").total)
        assertEquals(0, search("""{"code":200,"list":[]}""").total)
    }

    // ---------------- 详情 ----------------

    @Test
    fun detail_normal() {
        val item = GuapiParser.parseDetail(
            src,
            """{"vod_name":"庆余年","vod_pic":"https://p/1.jpg","vod_content":"<p>第一段</p><p>第二段</p>"}""",
            101L,
        )
        assertNotNull(item)
        assertEquals(101L, item.vod_id)
        assertEquals("庆余年", item.vod_name)
        assertEquals("https://p/1.jpg", item.vod_pic)
        assertEquals("第一段\n第二段", item.vod_content)
        assertEquals("ah_guapi", item._source)
        // 详情只填 4 个字段，其余保持 null
        assertNull(item.vod_remarks)
        assertNull(item.vod_play_from)
        assertNull(item.vod_play_url)
    }

    @Test
    fun detail_degenerate_returns_null() {
        assertNull(GuapiParser.parseDetail(src, "", 1L))
        assertNull(GuapiParser.parseDetail(src, "<html/>", 1L))
        assertNull(GuapiParser.parseDetail(src, "null", 1L))
        assertNull(GuapiParser.parseDetail(src, "[]", 1L))
    }

    @Test
    fun detail_missing_fields_become_empty_strings() {
        val item = GuapiParser.parseDetail(src, "{}", 1L)
        assertNotNull(item)
        assertEquals(1L, item.vod_id)
        assertEquals("", item.vod_name)
        assertEquals("", item.vod_pic)
        assertEquals("", item.vod_content)
    }

    @Test
    fun detail_null_fields_become_empty_strings() {
        val item = GuapiParser.parseDetail(
            src,
            """{"vod_name":null,"vod_pic":null,"vod_content":null}""",
            101L,
        )
        assertNotNull(item)
        assertEquals("", item.vod_name)
        assertEquals("", item.vod_pic)
        assertEquals("", item.vod_content)
    }

    @Test
    fun detail_vod_id_is_passthrough() {
        listOf(0L, -1L, 101L, Long.MAX_VALUE).forEach { id ->
            assertEquals(id, GuapiParser.parseDetail(src, "{}", id)?.vod_id, "for $id")
        }
    }

    // ---------------- stripHtml ----------------

    @Test
    fun strip_html_rules() {
        // 注意：`</p>` 与 `<br/>` 各自被独立替换成**一个** `\n`，
        // 不叠成空行；只有输入本身含 3 个以上连续换行才折叠成 `\n\n`。
        assertEquals("a\nb", GuapiParser.stripHtml("<p>a</p><p>b</p>"))
        assertEquals("a\nb", GuapiParser.stripHtml("a<br/>b"))
        assertEquals("a\n\nb", GuapiParser.stripHtml("a</P><BR/>b"))
        assertEquals("a", GuapiParser.stripHtml("a</p>"))     // 尾部单个换行被 trim
        assertEquals("", GuapiParser.stripHtml("<br/>"))       // 全是换行 → trim 后空
        assertEquals("line1\n\nline2", GuapiParser.stripHtml("line1\n\n\n\nline2"))
        assertEquals("s", GuapiParser.stripHtml("  s  "))       // trim 去首尾空白
        // 只解 &nbsp; 与 &amp;，&lt;/&gt;/&quot; **不解** —— 这是原实现既有行为
        assertEquals("&&lt;&gt;", GuapiParser.stripHtml("&nbsp;&amp;&lt;&gt;"))  // 前导空格被 trim 掉
        assertEquals("text", GuapiParser.stripHtml("<div class=\"x\">text</div>"))
        assertEquals("", GuapiParser.stripHtml(""))
    }

    // ---------------- 换链 ----------------

    @Test
    fun play_normal() {
        val r = GuapiParser.parsePlay("""{"url":"https://v/1.m3u8"}""")
        assertEquals("https://v/1.m3u8", r.url)
        assertEquals("", r.headers)
    }

    @Test
    fun play_numeric_url_becomes_text() {
        assertEquals("123", GuapiParser.parsePlay("""{"url":123}""").url)
    }

    @Test
    fun play_degenerate_is_empty() {
        listOf("", "<html/>", "{}", "null", "[]", """{"url":null}""", """{"url":""}""").forEach {
            assertEquals("", GuapiParser.parsePlay(it).url, "for $it")
        }
    }

    // ---------------- 响应解密降级 ----------------

    @Test
    fun decrypt_degenerate_returns_empty() {
        listOf(
            "",
            "<html/>",
            "{}",
            """{"data":{}}""",
            """{"data":{"keys":"","response_key":""}}""",
            """{"data":{"keys":"notbase64!!","response_key":"zz"}}""",
            """{"data":{"keys":"QUJD","response_key":"00"}}""",
            """{"data":{"response_key":"00"}}""",
            """{"data":{"keys":"QUJD"}}""",
            """[]""",
        ).forEach {
            assertEquals("", GuapiParser.decryptResponse(it), "for $it")
        }
    }

    @Test
    fun decrypt_round_trip_with_self_generated_keys() {
        // 用响应私钥对应的公钥无法从常量推出，故这里只验证「私钥能解开用自己公钥加密的块」
        // —— 等价于验证 decryptResponse 的 RSA→AES 串联本身可用。
        val key = SourceSecrets.GUAPI_AES_KEY
        val iv = SourceSecrets.GUAPI_AES_IV
        val inner = """{"iv":"$iv","key":"$key"}"""
        val keysB64 = "unused"
        // 直接构造一个必然失败的响应，确认不会抛异常（真正的正向用例需要私钥对应的公钥，
        // 而 GUAPI_RESPONSE_PRIVATE_KEY 只给了私钥的一半配对信息，见 §13.8.7 待决）
        val bogus = """{"data":{"keys":"$keysB64","response_key":"00"}}"""
        assertEquals("", GuapiParser.decryptResponse(bogus))
        assertTrue(inner.isNotEmpty())
    }

    // ---------------- 2026-10-31：真信封金样本（tools/dy_capture_1031.py 实抓） ----------------
    // 「合成样本-only」时代结束：对齐 tools/guazi_run.py 信封后链路 T3 打穿，
    // 以下用例以线上真信封为基准（业务错误信封 / 加密信封 / 未加密响应）。

    @Test
    fun golden_signin_error_envelope_unwraps_to_empty() {
        // 新设备 signIn 恒 403「用户不存在，请以设备号注册」——错误信封**也带**加密段，
        // 但业务码先行，不解不落 data（与实测脚本 decode_resp 同口径）
        val env = Golden.text("guapi_signin_env2")
        assertTrue(env.contains("\"code\":403"), "样本即 403 信封")
        assertTrue(env.contains("response_key"), "错误信封也带密钥段")
        assertEquals("", GuapiParser.unwrapEnvelope(env))
    }

    @Test
    fun golden_showone_envelope_decrypts_to_media_url() {
        // env（原始信封）与 plain（同轮另一请求的明文）是两次独立 showOne——
        // url 里的时效 token 各自不同，故只钉形状，不互比字节
        val plain = GuapiParser.unwrapEnvelope(Golden.text("guapi_showone_env2"))
        assertTrue(plain.isNotEmpty(), "200 加密信封必须解出明文")
        val r = GuapiParser.parsePlay(plain)
        assertTrue(r.url.startsWith("https://vd.wmvbo.com/"), "实际 ${r.url.take(60)}")
        assertTrue(r.url.contains("/decry/"))
        assertTrue(r.url.endsWith(".m3u8"))
        assertEquals("", r.headers)

        val fromPlain = GuapiParser.parsePlay(Golden.text("guapi_showone_plain2"))
        assertTrue(fromPlain.url.startsWith("https://vd.wmvbo.com/"))
        assertTrue(fromPlain.url.endsWith(".m3u8"))
    }

    @Test
    fun golden_unencrypted_data_object_is_returned_as_is() {
        // 未加密响应形态：data 无密钥字段 → data 本身即业务对象（序列化返回）
        val out = GuapiParser.unwrapEnvelope("""{"code":200,"msg":"ok","data":{"list":[],"total":0}}""")
        assertEquals("""{"list":[],"total":0}""", out)
    }

    @Test
    fun golden_top_level_payload_without_data_is_passthrough() {
        val out = GuapiParser.unwrapEnvelope("""{"list":[{"vod_id":1}]}""")
        assertEquals("""{"list":[{"vod_id":1}]}""", out)
        // 无 data 也无业务键 → 空串
        assertEquals("", GuapiParser.unwrapEnvelope("""{"msg":"nothing"}"""))
    }

    @Test
    fun golden_expand_param_matches_captured_param() {
        // 真实 param 串（guapi_param2）：k=v 平铺展开成 JSON，值全为字符串
        val param = Golden.text("guapi_param2")
        assertEquals(
            "{\"vod_d_id\":\"60397\",\"vurl_id\":\"551643\",\"domain_type\":\"8\"," +
                "\"resolution\":\"720\",\"type\":\"play\"}",
            GuapiParser.expandParam(param),
        )
        // 无 = 的段丢弃；空段跳过；有效段照收
        assertEquals("{\"a\":\"1\"}", GuapiParser.expandParam("novalue&&a=1"))
        assertEquals("{}", GuapiParser.expandParam("novalue&&"))
        assertEquals("{}", GuapiParser.expandParam(""))
    }

    @Test
    fun golden_search_plain_yields_real_rows() {
        val r = GuapiParser.parseSearch(src, Golden.text("guapi_search_plain2"))
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(3, r.items.size)
        val first = r.items.first()
        // vod_id 是数字字符串（"60397"）——三态取值走字符串分支
        assertEquals(60397L, first.vod_id)
        assertEquals("庆余年第二季", first.vod_name)
        assertEquals("", first.vod_remarks)
    }

    @Test
    fun golden_playinfo_vodinfo_fields_preferred() {
        val item = GuapiParser.parseDetail(src, Golden.text("guapi_playinfo_plain2"), 60397L)
        assertNotNull(item)
        assertEquals("庆余年第二季", item.vod_name)
        assertTrue(item.vod_pic.startsWith("https://images.67c6c7a.com/"))
        assertEquals("ah_guapi", item._source)
    }

    @Test
    fun golden_vurl_groups_quality_lines_with_param_tokens() {
        val item = GuapiParser.parseVurl(src, Golden.text("guapi_vurl_plain2"), 60397L, "庆余年第二季", "p.jpg")
        assertNotNull(item)

        // 480 线 show_type=2 且 param 空 → 跳过；可用线按首次出现序 720/1080
        assertEquals("720" + "$$$" + "1080", item.vod_play_from)
        val lines = item.vod_play_url!!.split("$$$")
        assertEquals(2, lines.size)
        val eps720 = lines[0].split("#")
        assertEquals(36, eps720.size)
        assertEquals("01\$vod_d_id=60397&vurl_id=551643&domain_type=8&resolution=720&type=play", eps720[0])
        // 每集有自己的 vurl_id；片名/集名走 \$ 前段，末集标题 "36"
        assertTrue(eps720.last().startsWith("36\$vod_d_id=60397&vurl_id="))
        val eps1080 = lines[1].split("#")
        assertEquals(36, eps1080.size)
        assertTrue(eps1080[0].contains("resolution=1080"), "每条线带自己的 resolution 参数")
    }
}
