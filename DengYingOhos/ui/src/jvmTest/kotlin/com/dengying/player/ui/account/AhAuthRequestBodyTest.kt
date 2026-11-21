package com.dengying.player.ui.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AH 账号请求体与错误文案回归（`ui/src/commonMain/.../account/AhAuth.kt`）。
 *
 * 契约来源：`D:\workspace\AHVideo\AH视频_账号体系登录注册接口文档.md`
 * - §5.2 服务端字段名是 `account`，只传 `username` 会 422；
 * - §5.9 QQ 登录走 `/auth/login` + `is_third_party=true`，`password` 与 `account` 同值；
 * - §5.4 注册账号格式为纯数字或邮箱；§5.5 错误码与文案。
 *
 * 这些是纯函数，HTTP 层不参与，所以能在 JVM 上直接锁住「字段名被改回 username」
 * 这类会让整条登录链静默失效的回归。
 */
class AhAuthRequestBodyTest {

    private val token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    @Test
    fun loginBodyUsesAccountFieldNotUsername() {
        val body = AhAuth.buildLoginBody("13800000000", "secret1", "1794755442", token)
        assertTrue(body.contains("\"account\":\"13800000000\""), body)
        assertFalse(body.contains("\"username\""), body)
        assertTrue(body.contains("\"password\":\"secret1\""), body)
        // ts 必须是字符串形态（服务端把毫秒数值判为 Expired）
        assertTrue(body.contains("\"ts\":\"1794755442\""), body)
        assertTrue(body.contains("\"app_auth_token\":\"$token\""), body)
    }

    @Test
    fun registerBodyWithoutInviteMatchesLoginBody() {
        val login = AhAuth.buildLoginBody("a@b.com", "secret1", "1794755442", token)
        val register = AhAuth.buildRegisterBody("a@b.com", "secret1", "1794755442", token, "   ")
        assertEquals(login, register)
        // 注册页已去掉邀请码输入框，调用方固定传空串：请求体不得残留 invite_code 字段
        assertFalse(register.contains("invite_code"), register)
    }

    @Test
    fun authErrorMessageHidesTokensAndEndpoints() {
        // 客户可见文案不出现令牌/接口/appid 等内部术语
        assertEquals(AUTH_UNAVAILABLE_TEXT, authErrorMessage("Invalid app_auth_token"))
        assertEquals(AUTH_UNAVAILABLE_TEXT, authErrorMessage("403 /auth/login 101983842"))
        assertEquals("网络不可用，稍后再试", authErrorMessage("网络连接失败"))
        assertEquals("注册太频繁，稍后再试", authErrorMessage("同一 IP 注册次数已达上限"))
        // 表单校验类文案原样给用户，说明该改哪里
        assertEquals("密码长度不能少于6位", authErrorMessage("密码长度不能少于6位"))
        assertEquals("请输入账号", authErrorMessage("请输入账号"))
        // 其余（账号密码错、未知服务端错误）收敛成一句，不泄露服务端原文
        assertEquals("账号或密码不对", authErrorMessage("Unauthorized"))
        assertEquals("账号或密码不对", authErrorMessage("Invalid JSON body"))
    }

    @Test
    fun registerBodySendsInviteCodeOnlyWhenFilled() {
        val body = AhAuth.buildRegisterBody("a@b.com", "secret1", "1794755442", token, " AB12 ")
        assertTrue(body.contains("\"invite_code\":\"AB12\""), body)
        assertTrue(body.contains("\"account\":\"a@b.com\""), body)
        assertFalse(body.contains("\"username\""), body)
    }

    @Test
    fun thirdPartyBodyFollowsCapturedQqContract() {
        val account = "70D1DFD19403EE18CD7B6EF1443CAAE8"
        val body = AhAuth.buildThirdPartyBody(
            account = account,
            nickname = "eclipse",
            avatarUrl = "http://thirdqq.qlogo.cn/x/0",
            ts = "1794755442",
            token = token,
        )
        assertTrue(body.contains("\"account\":\"$account\""), body)
        assertTrue(body.contains("\"password\":\"$account\""), body)
        assertTrue(body.contains("\"is_third_party\":true"), body)
        assertTrue(body.contains("\"nickname\":\"eclipse\""), body)
        assertTrue(body.contains("\"avatar_url\":\"http://thirdqq.qlogo.cn/x/0\""), body)
        assertTrue(body.contains("\"ts\":\"1794755442\""), body)
    }

    @Test
    fun numericOrEmailAcceptsOnlyDocumentedForms() {
        assertTrue(AhAuth.isNumericOrEmail("13800000000"))
        assertTrue(AhAuth.isNumericOrEmail("user@example.com"))
        assertFalse(AhAuth.isNumericOrEmail(""))
        assertFalse(AhAuth.isNumericOrEmail("abc"))
        assertFalse(AhAuth.isNumericOrEmail("user@example"))
        assertFalse(AhAuth.isNumericOrEmail("user@@example.com"))
        assertFalse(AhAuth.isNumericOrEmail("@example.com"))
    }

    @Test
    fun thirdPartyAccountRequires32UppercaseHex() {
        assertTrue(AhAuth.isThirdPartyAccount("70D1DFD19403EE18CD7B6EF1443CAAE8"))
        assertFalse(AhAuth.isThirdPartyAccount("70d1dfd19403ee18cd7b6ef1443caae8"))
        assertFalse(AhAuth.isThirdPartyAccount("70D1DFD19403EE18CD7B6EF1443CAAE"))
        assertFalse(AhAuth.isThirdPartyAccount("13800000000"))
    }

    @Test
    fun friendlyErrorMapsServerCodesToActionableChinese() {
        assertEquals(
            "请填写账号",
            AhAuth.friendlyError(422, "Missing field: account", isLogin = false),
        )
        assertEquals(
            "账号只能是数字或邮箱",
            AhAuth.friendlyError(422, "帐号只能是数字或邮箱格式", isLogin = false),
        )
        assertEquals(
            "密码长度不能少于6位",
            AhAuth.friendlyError(422, "密码长度必须> = 6", isLogin = false),
        )
        assertTrue(
            AhAuth.friendlyError(403, "Invalid app_auth_token", isLogin = true)
                .contains("签名令牌无效或已过期"),
        )
        assertTrue(
            AhAuth.friendlyError(403, "Expired app_auth_token", isLogin = true)
                .contains("校准系统时间"),
        )
        assertTrue(
            AhAuth.friendlyError(429, "同一 IP 注册次数已达上限", isLogin = false)
                .contains("注册次数已达上限"),
        )
        assertTrue(
            AhAuth.friendlyError(401, "无效的刷新令牌", isLogin = true).contains("会话令牌无效"),
        )
        assertTrue(
            AhAuth.friendlyError(401, "Unauthorized", isLogin = true).contains("登录已过期"),
        )
        assertTrue(
            AhAuth.friendlyError(503, "", isLogin = true).contains("暂时不可用"),
        )
        // 未知文案保留服务端原文，前面加动作前缀，方便用户截图反馈
        assertTrue(
            AhAuth.friendlyError(400, "Invalid JSON body", isLogin = true)
                .startsWith("登录失败："),
        )
    }
}
