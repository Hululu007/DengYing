package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import com.dengying.player.core.SourceSecrets
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Aes
import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.data.source.crypto.Protobuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 桔子（Juzhi）解析层长期守卫。
 *
 * 上游 `http://103.36.167.27:18004` 实测 502，拿不到真实响应，
 * 因此全部样本都是**按协议手工构造的 protobuf 字节**（等价于原实现逆向出的结构）。
 *
 * 断言的取值来自批 C-7 的 `JuzhiDumpTest` 实测输出，不是脑补；
 * 涉及随机填充的地方（RSA 密文、随机串）只断言长度/可解密性，不断言字面量。
 */
class JuzhiGoldenTest {

    /** `$` 在 Kotlin 字符串里是模板起始符，且中文是合法标识符字符，故必须用常量拼接。 */
    private val D = "$"

    private val src = CmsSource(
        key = "ah_juzhi",
        name = "桔子",
        api = "http://103.36.167.27:18004",
        speed = 26,
        direct = false,
        enabled = true,
        tier = 3,
        protocol = SourceProtocol.Juzhi,
        caps = SourceCaps(search = true, category = false, detail = false, token = true),
    )

    // ---------------------------------------------------------------- 构造样本

    /** DramaItem：f3=id(varint), f5=name, f2=pic(bytes), f13=remarks, f14=num */
    private fun dramaItem(id: Long, name: String, pic: String, remarks: String, num: Long): ByteArray {
        val img = Protobuf.Writer().stringField(1, pic).toBytes()
        return Protobuf.Writer()
            .varintField(3, id)
            .stringField(5, name)
            .bytesField(2, img)
            .stringField(13, remarks)
            .varintField(14, num)
            .toBytes()
    }

    private fun searchPage(vararg items: ByteArray): ByteArray {
        val w = Protobuf.Writer()
        for (i in items) w.bytesField(1, i)
        return w.toBytes()
    }

    /** Video：f4=path, f9=机器标识, f10=中文名, f2=标题 */
    private fun video(path: String, srcId: String, srcCn: String, title: String): ByteArray =
        Protobuf.Writer()
            .stringField(4, path)
            .stringField(9, srcId)
            .stringField(10, srcCn)
            .stringField(2, title)
            .toBytes()

    private fun hdr(k: String, v: String): ByteArray =
        Protobuf.Writer().stringField(1, k).stringField(2, v).toBytes()

    // ---------------------------------------------------------------- 请求头 / 设备参数

    @Test
    fun v_app_and_random_helpers() {
        assertEquals("3023", JuzhiParser.V_APP)
        assertEquals(8, JuzhiParser.rnd(8).length)
        assertEquals(16, JuzhiParser.rnd(16).length)

        val uuid = JuzhiParser.newUuid()
        assertEquals(32, uuid.length)
        assertTrue(uuid.all { it in "0123456789abcdef" }, "uuid 必须是小写 hex：$uuid")
    }

    @Test
    fun v5_headers_shape() {
        val h = JuzhiParser.v5Headers(
            SourceSecrets.JUZHI_PRIMARY_PUBLIC_KEY, 1700000000000L, "abc123",
            "0123456789abcdef0123456789abcdef",
        )
        assertEquals(listOf("Accept", "Content-Type", "User-Agent", "publicParams"), h.keys.sorted())
        assertEquals("okhttp/3.12.1", h["User-Agent"])
        assertEquals("application/x-protobuf", h["Accept"])
        assertEquals("application/x-protobuf", h["Content-Type"])

        val raw = h["publicParams"] ?: ""
        assertTrue(raw.startsWith("{\"paramsData\":\""), "头形态：$raw")
        val pd = JsonX.str(JsonX.parseObject(raw), "paramsData", "")
        assertTrue(pd.isNotEmpty())
        assertTrue(pd.all { it in "0123456789abcdef" }, "paramsData 必须是小写 hex")
        assertEquals(0, pd.length % 2, "hex 长度必须为偶数")
    }

    @Test
    fun public_params_decrypts_to_device_params_json() {
        val uuid = "0123456789abcdef0123456789abcdef"
        val raw = JuzhiParser.v5Headers(SourceSecrets.JUZHI_PRIMARY_PUBLIC_KEY, 1700000000000L, "abc123", uuid)["publicParams"] ?: ""
        val pd = JsonX.str(JsonX.parseObject(raw), "paramsData", "")

        // key 与 iv 同为 JUZHI_PUBLIC_PARAMS_KEY（协议如此），解出来必须是设备参数 JSON。
        // 注意：v5Headers 内部自行计算 sig/sig2/sig3（含 RSA 随机填充），故不能与手工
        // deviceParamsJson(..., "SIG", ...) 的返回值逐字相等 —— 这里校验结构与可变字段。
        val plain = Digest.utf8String(
            Aes.cbcDecrypt(
                "AES128",
                Digest.utf8Bytes(SourceSecrets.JUZHI_PUBLIC_PARAMS_KEY),
                Digest.utf8Bytes(SourceSecrets.JUZHI_PUBLIC_PARAMS_KEY),
                Aes.hexToBytesSafe(pd),
            ),
        )
        assertTrue(plain.startsWith("{\"country\":\"CN\",\"vName\":\"3.0.2.3\""), "明文头：${plain.take(60)}")
        assertTrue(plain.contains("\"timestamp\":1700000000000"), "明文须含时间戳")
        assertTrue(plain.contains("\"random_str\":\"abc123\""), "明文须含 random_str")
        assertTrue(plain.contains("\"uuid\":\"$uuid\""), "明文须含 uuid")
        assertTrue(plain.endsWith("}"), "明文须是完整 JSON 对象")
        // 键序与手工构造版一致（服务端按序验签）
        assertEquals(
            JsonX.keys(JsonX.parseObject(JuzhiParser.deviceParamsJson(1700000000000L, "abc123", "0123456789abcdef", "S", "3023", uuid))!!),
            JsonX.keys(JsonX.parseObject(plain)!!),
            "键序必须一致",
        )
    }

    @Test
    fun device_params_key_order_is_frozen() {
        val uuid = "0123456789abcdef0123456789abcdef"
        val params = JuzhiParser.deviceParamsJson(1700000000000L, "abc123", "0123456789abcdef", "SIG", "3023", uuid)

        assertEquals(
            "country,vName,cpuId,young,facturer,pkg,uuid,resolution,mac,abid,model,plat,udid,dpi," +
                "net,lang,brand,density,appName,cpu,chid,carrier,_vOsCode,vOs,v,tenantId,vApp,device," +
                "androidID,timestamp,random_str,sig,sig2,sig3",
            JsonX.keys(JsonX.parseObject(params)!!).joinToString(","),
            "键序是服务端验签的一部分，不可变动",
        )
        assertTrue(params.startsWith("{\"country\":\"CN\",\"vName\":\"3.0.2.3\""), "头：${params.take(60)}")
        assertTrue(
            params.endsWith(
                "\"timestamp\":1700000000000,\"random_str\":\"abc123\"," +
                    "\"sig\":\"SIG\",\"sig2\":\"01234567\",\"sig3\":\"89abcdef\"}",
            ),
            "尾：${params.takeLast(120)}",
        )
        assertEquals(681, params.length)
        assertEquals(34, JsonX.keys(JsonX.parseObject(params)!!).size)
    }

    // ---------------------------------------------------------------- zone 二级公钥

    @Test
    fun zone_request_bytes() {
        val zf = Protobuf.readFields(JuzhiParser.zoneRequestBytes(1700000000000L, "abc123"))
        assertEquals(listOf(1, 2, 3, 4, 5), zf.map { it.fieldNo })
        assertEquals(1700000000000L, Protobuf.varintOf(zf, 1))

        val f2 = Digest.utf8String(Protobuf.bytesOf(zf, 2))
        assertEquals(172, f2.length, "RSA 密文 Base64 长度")
        assertTrue(f2.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }, "f2 必须是 Base64")

        assertEquals(16, Digest.utf8String(Protobuf.bytesOf(zf, 3)).length)
        assertEquals("abc123", Digest.utf8String(Protobuf.bytesOf(zf, 4)))
        assertEquals(16, Digest.utf8String(Protobuf.bytesOf(zf, 5)).length)
    }

    @Test
    fun zone_key_parse() {
        val inner = Protobuf.Writer()
            .stringField(1, "IGNORED")
            .stringField(2, "MIGf")
            .stringField(3, "MA0G")
            .stringField(4, "CSqG")
            .stringField(5, "SIb3")
            .toBytes()
        val ok = Protobuf.Writer().varintField(1, 200L).bytesField(3, inner).toBytes()

        // f1..f5 文本顺序拼接，跳过 f1
        assertEquals("MIGfMA0GCSqGSIb3", JuzhiParser.zoneKeyOf(ok))
        assertEquals("", JuzhiParser.zoneKeyOf(Protobuf.Writer().varintField(1, 500L).bytesField(3, inner).toBytes()))
        assertEquals("", JuzhiParser.zoneKeyOf(ByteArray(0)))
    }

    // ---------------------------------------------------------------- 业务请求体

    @Test
    fun request_body_roundtrip() {
        val bf = Protobuf.readFields(
            JuzhiParser.requestBodyBytes(
                mapOf("searchKeys" to "庆余年", "page" to "1", "pagesize" to "21", "empty" to ""),
                1700000000000L, "RND8RND8",
            ),
        )
        assertEquals(listOf(1, 2, 3, 4, 5), bf.map { it.fieldNo })
        assertEquals(1700000000000L, Protobuf.varintOf(bf, 4))
        assertEquals("RND8RND8", Digest.utf8String(Protobuf.bytesOf(bf, 5)))

        val f1 = Digest.utf8String(Protobuf.bytesOf(bf, 1))
        val f2 = Digest.utf8String(Protobuf.bytesOf(bf, 2))
        assertEquals(20, f1.length)
        assertEquals(20, Digest.utf8String(Protobuf.bytesOf(bf, 3)).length)

        // mixed = random8 + Base64(密文)，f1 取前 20，f2 取余下
        val mixed = f1 + f2
        assertTrue(mixed.startsWith("RND8RND8"), "mixed 必须以 random8 开头：$mixed")
        assertEquals(20 + f2.length, mixed.length, "mixed = f1(20 字符) + f2")

        // mixed = random8 + Base64(AES-256-ECB(pairs + ts))；f1 只取前 20 字符，
        // 故 f2 是**被截断的 Base64 片段**，必须从拼回的 mixed 里剥掉 random8 再解码。
        val plain = Digest.utf8String(
            Aes.ecbDecrypt(
                "AES256",
                Digest.utf8Bytes(SourceSecrets.JUZHI_KEY_1),
                Digest.base64DecodeBytes(mixed.substring(8)),
            ),
        )
        assertTrue(plain.contains("searchKeys=庆余年"), "明文：$plain")
        assertTrue(plain.contains("page=1"), "明文：$plain")
        assertTrue(plain.contains("pagesize=21"), "明文：$plain")
        assertFalse(plain.contains("empty="), "空值字段必须被跳过：$plain")
        assertTrue(plain.endsWith("1700000000000"), "明文必须以时间戳结尾：$plain")
    }

    @Test
    fun api_data_parse() {
        val ok = Protobuf.Writer().varintField(1, 200L).bytesField(3, "PAYLOAD".encodeToByteArray()).toBytes()
        assertEquals("PAYLOAD", Digest.utf8String(JuzhiParser.apiDataOf(ok)!!))

        val bad = Protobuf.Writer().varintField(1, 100010L).bytesField(3, "X".encodeToByteArray()).toBytes()
        assertNull(JuzhiParser.apiDataOf(bad), "code != 200 必须返回 null")
        assertNull(JuzhiParser.apiDataOf(ByteArray(0)))
    }

    // ---------------------------------------------------------------- 搜索

    @Test
    fun search_two_items() {
        val page = searchPage(
            dramaItem(1001L, "庆余年 第二季", "https://p/1.jpg", "全36集", 36L),
            dramaItem(1002L, "庆余年", "https://p/2.jpg", "全46集", 46L),
        )
        val r = JuzhiParser.parseSearch(src, page)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(2, r.items.size)
        assertEquals(2, r.total)
        assertEquals(1, r.pageCount)

        assertEquals(1001L, r.items[0].vod_id)
        assertEquals("庆余年 第二季", r.items[0].vod_name)
        assertEquals("https://p/1.jpg", r.items[0].vod_pic)
        // num > 0 时拼在备注后面
        assertEquals("全36集 36", r.items[0].vod_remarks)
        assertEquals("全46集 46", r.items[1].vod_remarks)
        assertEquals("ah_juzhi", r.items[0]._source)
    }

    @Test
    fun search_degraded_states() {
        val empty = JuzhiParser.parseSearch(src, ByteArray(0))
        assertEquals(RequestOutcome.RequestFailed, empty.outcome)
        assertEquals("响应为空", empty.err)

        // 有响应但 f1（行数组）为空 → 空结果，不是失败
        val noRows = JuzhiParser.parseSearch(src, Protobuf.Writer().varintField(2, 1L).toBytes())
        assertEquals(RequestOutcome.SuccessNoData, noRows.outcome)
        assertEquals(0, noRows.items.size)
        assertEquals(0, noRows.total)
    }

    @Test
    fun search_skip_rules() {
        val page = searchPage(
            dramaItem(0L, "zeroId", "P", "", 1L),
            dramaItem(8L, "", "P", "", 1L),
            dramaItem(9L, "ok", "P", "", 1L),
        )
        assertEquals(listOf(9L), JuzhiParser.parseSearch(src, page).items.map { it.vod_id })
    }

    @Test
    fun search_id_text_fallback() {
        // f3 是文本时按数字解析
        val textId = Protobuf.Writer().stringField(3, "1234").stringField(5, "T").toBytes()
        assertEquals(1234L, JuzhiParser.parseSearch(src, searchPage(textId)).items.firstOrNull()?.vod_id)

        // 解析不出数字 → 整行跳过
        val badId = Protobuf.Writer().stringField(3, "abc").stringField(5, "T").toBytes()
        assertEquals(0, JuzhiParser.parseSearch(src, searchPage(badId)).items.size)
    }

    @Test
    fun search_pic_falls_back_to_f2() {
        val img2 = Protobuf.Writer().stringField(2, "https://p/f2.jpg").toBytes()
        val item = Protobuf.Writer().varintField(3, 11L).stringField(5, "N").bytesField(2, img2).toBytes()
        assertEquals("https://p/f2.jpg", JuzhiParser.parseSearch(src, searchPage(item)).items[0].vod_pic)
    }

    @Test
    fun search_remarks_with_non_positive_num() {
        // num <= 0 时不拼数字
        val page = searchPage(dramaItem(7L, "N", "P", "", -1L))
        assertEquals("", JuzhiParser.parseSearch(src, page).items[0].vod_remarks)
    }

    // ---------------------------------------------------------------- 详情

    private fun fullDetail(): ByteArray = Protobuf.Writer()
        .stringField(9, "庆余年 第二季")
        .bytesField(2, Protobuf.Writer().stringField(1, "https://p/d.jpg").toBytes())
        .stringField(25, "张若昀,李沁")
        .stringField(26, "全36集")
        .varintField(18, 2024L)
        .stringField(7, "简介A")
        .stringField(6, "简介B")
        .bytesField(29, video("/vodplay/1-1-1", "Ksvideo", "蓝光1", "第1集"))
        .bytesField(29, video("/vodplay/1-1-2", "Ksvideo", "蓝光1", "第2集"))
        .bytesField(29, video("/vodplay/1-2-1", "bytedance", "蓝光2", ""))
        .bytesField(29, video("https://v/3.m3u8", "zijianm3u8", "蓝光3", "第1集"))
        .toBytes()

    @Test
    fun detail_basic_fields() {
        val d = JuzhiParser.parseDetail(src, fullDetail(), 1001L)!!
        assertEquals("庆余年 第二季", d.vod_name)
        assertEquals("https://p/d.jpg", d.vod_pic)
        assertEquals("张若昀,李沁", d.vod_actor)
        assertEquals("全36集", d.vod_remarks)
        assertEquals("2024", d.vod_year)
        // f7 优先，f6 兜底
        assertEquals("简介A", d.vod_content)
        assertEquals(1001L, d.vod_id)
        assertEquals("ah_juzhi", d._source)
    }

    @Test
    fun detail_line_grouping_by_f9() {
        val d = JuzhiParser.parseDetail(src, fullDetail(), 1001L)!!
        // 线路名用 f10 中文，三组按 f9 归并
        assertEquals("蓝光1" + D + D + D + "蓝光2" + D + D + D + "蓝光3", d.vod_play_from)

        val url = d.vod_play_url!!
        assertEquals(255, url.length, "token 长度钉死，防 Base64/JSON 形态漂移")
        val lines = url.split(D + D + D)
        assertEquals(3, lines.size)
        assertEquals(2, lines[0].split("#").size)
        assertEquals(1, lines[1].split("#").size)
        assertEquals(1, lines[2].split("#").size)

        // 第一集：名字 + token，token 能解回路径与线路标识
        val first = lines[0].split("#")[0]
        assertEquals("第1集", first.substringBefore(D))
        val p = JuzhiParser.playParamsOf(first.substringAfter(D), "?")!!
        assertEquals("/vodplay/1-1-1", p.playUrl)
        assertEquals("Ksvideo", p.vodPlayFrom)

        // 缺标题时回退成「第N集」；这里第三组只有一集，标题是「第1集」
        assertEquals("第1集", lines[1].split("#")[0].substringBefore(D))

        // 已是媒体地址时直接存原地址，不包 token
        assertEquals("第1集" + D + "https://v/3.m3u8", lines[2])
    }

    @Test
    fun detail_degraded_states() {
        assertNull(JuzhiParser.parseDetail(src, ByteArray(0), 1L))
        // 无 f9（名称）→ null
        assertNull(JuzhiParser.parseDetail(src, Protobuf.Writer().stringField(26, "x").toBytes(), 1L))

        // 无剧集：from 与 url 都是空串
        val noEps = JuzhiParser.parseDetail(src, Protobuf.Writer().stringField(9, "N").toBytes(), 1L)!!
        assertEquals("", noEps.vod_play_from)
        assertEquals("", noEps.vod_play_url)

        // 年份 0 → 空串（不是 "0"）
        val yearZero = JuzhiParser.parseDetail(
            src, Protobuf.Writer().stringField(9, "N").varintField(18, 0L).toBytes(), 1L,
        )!!
        assertEquals("", yearZero.vod_year)

        // f7 为空时用 f6
        val contentFallback = JuzhiParser.parseDetail(
            src, Protobuf.Writer().stringField(9, "N").stringField(6, "B").toBytes(), 1L,
        )!!
        assertEquals("B", contentFallback.vod_content)
    }

    // ---------------------------------------------------------------- token 解码

    @Test
    fun token_decode() {
        val tok = Digest.base64Encode(
            Digest.utf8Bytes(JsonX.buildObject("vodPlayFrom" to "Ksvideo", "playUrl" to "/vodplay/1-1-1").toString()),
        )
        val p = JuzhiParser.playParamsOf(tok, "dflt")!!
        assertEquals("/vodplay/1-1-1", p.playUrl)
        assertEquals("Ksvideo", p.vodPlayFrom)

        // 不是 Base64 JSON → null（由调用方回落成原 token）
        assertNull(JuzhiParser.playParamsOf("!!!notb64!!!", "dflt"))
        assertNull(JuzhiParser.playParamsOf("https://v/x.m3u8", "dflt"))

        // 缺 vodPlayFrom 时用调用方传入的 from 兜底
        val noFrom = Digest.base64Encode(Digest.utf8Bytes("{\"playUrl\":\"/p\"}"))
        val p2 = JuzhiParser.playParamsOf(noFrom, "dflt")!!
        assertEquals("/p", p2.playUrl)
        assertEquals("dflt", p2.vodPlayFrom)
    }

    // ---------------------------------------------------------------- 换链

    @Test
    fun play_ok_with_headers() {
        val body = Protobuf.Writer()
            .stringField(1, "https://v/1.m3u8")
            .bytesField(6, hdr("Referer", "https://a/"))
            .bytesField(6, hdr("User-Agent", "UA"))
            .toBytes()
        val r = JuzhiParser.parsePlay(body)
        assertEquals("https://v/1.m3u8", r.url)
        assertEquals(listOf("Referer", "User-Agent"), JsonX.keys(JsonX.parseObject(r.headers)!!).sorted())
        assertEquals("https://a/", JsonX.str(JsonX.parseObject(r.headers), "Referer", ""))
        assertEquals("UA", JsonX.str(JsonX.parseObject(r.headers), "User-Agent", ""))
    }

    @Test
    fun play_degraded_states() {
        // 无头：headers 是 "{}"（不是 ""），与 org.json 版字节形态一致
        val noHdr = Protobuf.Writer().stringField(1, "https://v/2.m3u8").toBytes()
        assertEquals("{}", JuzhiParser.parsePlay(noHdr).headers)

        // f9 = 失败原因：f1 是回显的入参 token，绝不能当播放地址
        val fail = Protobuf.Writer()
            .stringField(1, "Ksvideo-abc")
            .stringField(9, "找不到视频源对应的解析配置")
            .toBytes()
        assertEquals("", JuzhiParser.parsePlay(fail).url)

        // 非 http 开头 → 空
        assertEquals("", JuzhiParser.parsePlay(Protobuf.Writer().stringField(1, "ftp://x").toBytes()).url)
        assertEquals("", JuzhiParser.parsePlay(ByteArray(0)).url)

        // key 为空的自定义头被跳过
        val emptyKey = Protobuf.Writer()
            .stringField(1, "https://v/3.m3u8")
            .bytesField(6, hdr("", "V"))
            .toBytes()
        assertEquals("{}", JuzhiParser.parsePlay(emptyKey).headers)
    }

    // ---------------------------------------------------------------- 工具函数

    @Test
    fun is_media_url() {
        assertTrue(JuzhiParser.isMediaUrl("a.m3u8"))
        assertTrue(JuzhiParser.isMediaUrl("a.MP4"), "大小写不敏感")
        assertTrue(JuzhiParser.isMediaUrl("a.m3u8?x=1"))
        assertTrue(JuzhiParser.isMediaUrl("a.m3u8#f"))
        assertFalse(JuzhiParser.isMediaUrl("a.xyz"))
        assertFalse(JuzhiParser.isMediaUrl("noext"))
        assertFalse(JuzhiParser.isMediaUrl("/vodplay/1-1-1"))
    }

    @Test
    fun id_of_prefers_varint() {
        assertEquals(42L, JuzhiParser.idOf(Protobuf.readFields(Protobuf.Writer().varintField(3, 42L).toBytes())))
        assertEquals(77L, JuzhiParser.idOf(Protobuf.readFields(Protobuf.Writer().stringField(3, " 77 ").toBytes())))
        assertEquals(0L, JuzhiParser.idOf(Protobuf.readFields(Protobuf.Writer().stringField(5, "x").toBytes())))
    }
}
