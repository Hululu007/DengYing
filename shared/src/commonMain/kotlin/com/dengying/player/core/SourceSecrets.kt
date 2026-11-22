package com.dengying.player.core

/**
 * 签名密钥与协议常量集中处 —— **审计唯一入口**
 *
 * 背景：文才源用固定共享密钥做 `SHA1(MD5(...))` 请求签名，该密钥是协议常量、
 * 服务端校验必需，无法在运行期协商。项目约束 SEC-003/FR-604「凭据形态参数不进仓库」
 * 是**针对抓包所得的一次性签名参数**（sign/session/cookie/guid），
 * 本文件收录的是**协议级固定常量**，其取舍与边界由 ADR-0006 决策记录承载。
 *
 * 纪律：
 *  - 一次性签名、会话、cookie、设备标识**一律不得**写进本文件
 *  - 本文件只允许出现「公开协议常量」；新增项必须在 ADR-0006 附表登记
 *  - 日志不得输出本文件任何常量的值（SEC-004）
 *
 * 【移植说明】逐字搬运自鸿蒙版 `common/SourceSecrets.ets`，未做任何改动。
 * 这些常量是协议的**公开参数**（该源所有客户端都须计算同样的签名），
 * 不是凭据；但仍是敏感面，改动前必须回看 ADR-0006。
 */
object SourceSecrets {

    // ==================== 文才（mw-movie 匿名接口） ====================

    /** 文才请求签名密钥 */
    const val WENCAI_SIGN_KEY: String = "cb808529bae6b6be45ecfab29a4889bc"

    /** 文才签名参与串（搜索）：参数串原文，`keyword` 不 URL 编码 */
    fun wencaiSearchSignRaw(keyword: String, ts: String): String =
        "keyword=$keyword&pageNum=1&pageSize=8&key=$WENCAI_SIGN_KEY&t=$ts"

    /** 文才签名参与串（详情） */
    fun wencaiDetailSignRaw(vodId: String, ts: String): String =
        "id=$vodId&key=$WENCAI_SIGN_KEY&t=$ts"

    /** 文才签名参与串（播放换链） */
    fun wencaiPlaySignRaw(vodId: String, nid: String, ts: String): String =
        "clientType=3&id=$vodId&nid=$nid&key=$WENCAI_SIGN_KEY&t=$ts"

    /** 米米4k 的移动端 UA（服务端按 UA 分派响应） */
    const val MINO_UA: String =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

    // ==================== 加密体协议常量（ADR-0006 附表登记） ====================

    /** 桔子（橘汁）：请求体 AES 密钥与二级签名密钥 */
    const val JUZHI_KEY_1: String = "OW1WBLFZCLJ0WTNJCDMYEGXWYVP3PT0="
    const val JUZHI_KEY_2: String = "OC1A06E197EF10CF3F6058CA7A803B5E"
    const val JUZHI_PUBLIC_PARAMS_KEY: String = "ed5fdsgucxumegqa"
    const val JUZHI_UA: String = "okhttp/3.12.1"

    /** 一级公钥（X.509 SPKI，base64 体，无 PEM 头） */
    const val JUZHI_PRIMARY_PUBLIC_KEY: String =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCr8SzZhjYy+rsya1K09t8d2K50pWFoBkgUqMpKOiW+3IEVKd4eTdvg9RSOjQ82kypL6R9BnsmrS1V8s4PVDwjQbUtYhTPPC9Hz16qY7rpD6m0d2vr09/UpWQ5uOy9PR0QTrsioveZ+DIe9jc3C+zBCu/kZSY/R8stwJoiitki3gwIDAQAB"

    // ==================== 瓜皮影视 ====================

    /** 表单 AES 密钥/IV、签名盐、旧设备密钥 */
    const val GUAPI_AES_KEY: String = "OITxa5OqAYjhswxx"
    const val GUAPI_AES_IV: String = "rCMNwZASNBKZ8mXV"
    const val GUAPI_SIGNATURE_SECRET: String = "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br"
    const val GUAPI_OLD_KEY: String = "aLFBMWpxBrIDAD1Si/KVvm41"

    /**
     * 请求壳字段（2026-10-31 真信封实测对齐 tools/guazi_run.py，ADR-0006 附表登记）：
     * 服务端按这些头识别客户端形态，缺任一项走异常分支（曾因此误判鉴权链不通）。
     */
    const val GUAPI_UA: String = "Lavf/57.83.100"
    const val GUAPI_VERSION_NAME: String = "3.0.3.2"
    const val GUAPI_VERSION_CODE: String = "2604028"
    const val GUAPI_PACKAGE: String = "com.ae06aebdbb.y286327f5a.ofe849883320260517"
    const val GUAPI_CHANNEL: String = "GZ0369"
    const val GUAPI_PHONE_MODEL: String = "xiaomi-22081212c"

    /** 播放流（vd.wmvbo.com）防盗链 Referer */
    const val GUAPI_STREAM_REFERER: String = "http://WJiZxLXA2.com/"

    const val GUAPI_REQUEST_PUBLIC_KEY: String =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB"

    const val GUAPI_RESPONSE_PRIVATE_KEY: String =
        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcKZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcWV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdIDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVMiMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUMWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1bL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oat5lYKfpe8k83ZA=="

    // ==================== 山楂4k ====================

    /** 请求公钥（1024）与响应私钥（2048），两者非同一对 */
    const val SHANZHA_REQUEST_PUBLIC_KEY: String =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCoYt0BP77U+DM08BiI/QbSRIfxijXo85BTPqIM1Ow8BNwhLETzRIZ+dEwdWDbydG/PspgBAfRpGaYVdJYtvaC2JnoO8+Ik6qMWojfEJxSFLa0Pb0A892tun4gsxoEMjcreZ+YGyaBxAfqX0BSMfdrOgIYaZQjYrw9TRLlUT31QoQIDAQAB"

    const val SHANZHA_RESPONSE_PRIVATE_KEY: String =
        "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCquQQ5r6+yJI8CDFkXRp8vUsdD45ov8EP12ooLs56ca2DQXaSNGS9910bAPVA9chkp0mKIvKqjAsHz5Tl9EeNPblarGEeJUIxpxZtiSqNTpvtiD/TjhpzuHYic7RAfQ/h7p/ypE8ymU42pYjsB5t26Mv6XgkLV+jzrSf73HlCuS0iMyLmt6zz3Mw9izM13EpB8iFLtfbbYymycKTx4RAmPQLwhNGex/AlUIYxXP4R2yyaa4W6mEtc6aME2QuzJFxPgP3HJ9NBx/LWVn4skxWjZ7zg+VRQRHnjyVaSLu3Z5gN5ITWCyE32qaHJa6WBahZj5jWhRyAG1bQ+xKJa8lBL5AgMBAAECggEAUwv9SjJ0PSwbhNuM2w23kcWquROWhYtTA91zGY4esehqB/IFgb2mpIh8Gje5OKqwIu/8jpd4SiOlRYdUF8sD0DfUYRZGdj2AkFNX6tBz8tVfo6wvbB6naA1lzzBij1L5JO3qsjS3cJFkb+kg2yP66AC2Z+0tpfk8eRhdtshAZwfcd1DEGt1uAvYL1eaUK9HRvpt9lPeGcHERDl2hBd4uyaF0K1O+zF9y59nYbTySWPxRZq3sFEE85xRMlstD7YZi7W2gKvMFRD4/FKmrZ3m7aKJRITtyKOyyPcYmepNv3Qv7kk59Pg38n2WWQ0Ra/bCH3E48YNCnQvZMpitkTfJhoQKBgQDbnROOYTP8OTJ6f/qhoGjxeO3x1VOaOp8l0x7b0SCfoqNGS0Cyiqj72BmJtPMPqSTjn6MmNzqbg1KOdhXyzNozs+i5ccW1M56j96mr5I/Z0FpE3oyIHNfDDBlf9M8YQqEF9oYxniYYft9oapO7cRQkHER6qpvnHTavwlv4m78CXwKBgQDHAjs2YlpKDdI1lcbZJCc7TwtH+Pd2bUki8YXafWNcPhITQHbOZjr310eK1QJC6GJncjkOqbX7yv3ivvTO35FZTQhuA1xEG1P00FG8bE0tHYPIwQHi9y0eA5cieMdo8E6XYria1mw/3fqSQEsfZyJlR32JQIoGAipM8iO1X2nZpwKBgDkMFIhnt5lNQk+P7wsNIDWZtDWdtJnboHuy29E+Abt2A/O+mI/IdRz2hau/1WO8DFkUnszOi+rZshhPlGP90rCbi1igtTrcrdjp/KkqNjPea5R4OwkgdOu1uOG0NheXNzzVTQaWjk7Opjn5dWa7eP/oV+GFb/oZHJuLYVizHGsBAoGADA7rjZEKDYCm4w5PPSr+oY5ZjaPdQrS+gLqHtMRyN82fBMGcMUdqfUfzEstzVqCEDeaS5HuOBlK3bXzKkppjUTjksN3NQmcxgBz7RuJ9DqXCLXDcb2cwuafYCYOt+YLOEEgwDVm+t2P44dG5e46hO+fICH/7nP+WlpD5buz4GfMCgYB57r3g/6hi9WUDnfc7ZAzWMqR0EhJVYKYy+KFEtdIPzhkkIHq5RASe88E9kzoGoZFdb3tIjvGZWcHerirrqWkMsuQtP/Qi0zjieid5tAPj+r4kbiCVTw0E0jnmPBzGInQi7lpeTTKnG1fbyS5lBS+WmHfIuzpECgCkxhaT+LJJkg=="

    /** deviceId 前缀（瓜皮客户端形态：18 位数字） */
    const val GUAPI_DEVICE_PREFIX: String = "21643673945591"
}
