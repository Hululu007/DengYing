package com.dengying.player.data.source.crypto

/**
 * 最小 protobuf 读写器（wire type 0/1/2/5）
 *
 * 用途：桔子源的请求/响应体是 protobuf（无 .proto 工具链，字段表由规则逆向得出）。
 * 只实现逆向所需的子集：varint、64 位定长、32 位定长（跳过）、长度前缀；不处理 map/group。
 *
 * 【关键：wire type 5 必须跳过而不是中断】
 * 桔子 Drama 的 f10 是 fixed32；若解析器遇到未知 wire type 就 break，
 * f10 之后的字段（含 repeated Video 分集）会全部丢失，表现为"详情拿到名字但一集都没有"。
 * 原 ArkTS 版在这一点上已有注释，此处实现保持同样行为。
 */
object Protobuf {

    /** 单个字段 */
    data class Field(
        val fieldNo: Int,
        val wireType: Int,
        /** wireType=0 时的值 */
        val varint: Long = 0,
        /** wireType=1 时的 8 字节 */
        val fixed64: ByteArray = ByteArray(0),
        /** wireType=2 时的原始字节 */
        val bytes: ByteArray = ByteArray(0),
    )

    /** 写入器 */
    class Writer(initialCapacity: Int = 512) {
        private var buf = ByteArray(initialCapacity)
        private var len = 0

        private fun ensure(extra: Int) {
            if (len + extra <= buf.size) return
            var cap = buf.size * 2
            while (cap < len + extra) cap *= 2
            val next = ByteArray(cap)
            buf.copyInto(next, 0, 0, len)
            buf = next
        }

        private fun pushByte(b: Int) {
            ensure(1)
            buf[len] = b.toByte()
            len++
        }

        /**
         * varint 写入。
         *
         * 用 Long 而不是 Int：protobuf 的 varint 是无符号语义，
         * 32 位值 0x80000000 起用 Int 会死循环（`v > 0x7f` 恒真且移位补 1）。
         */
        private fun pushVarint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                pushByte(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            pushByte((v and 0x7F).toInt())
        }

        /** 字段号 + varint 值 */
        fun varintField(fieldNo: Int, value: Long): Writer {
            pushVarint((fieldNo.toLong() shl 3) or 0)
            pushVarint(value)
            return this
        }

        /** 字段号 + 长度前缀字节 */
        fun bytesField(fieldNo: Int, data: ByteArray): Writer {
            pushVarint((fieldNo.toLong() shl 3) or 2)
            pushVarint(data.size.toLong())
            ensure(data.size)
            data.copyInto(buf, len)
            len += data.size
            return this
        }

        /** 字段号 + UTF-8 字符串 */
        fun stringField(fieldNo: Int, text: String): Writer =
            bytesField(fieldNo, Digest.utf8Bytes(text))

        fun size(): Int = len

        fun toBytes(): ByteArray = buf.copyOfRange(0, len)
    }

    /** 读 varint，返回 (值, 新位置) */
    private fun readVarint(bytes: ByteArray, pos: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var p = pos
        while (p < bytes.size) {
            val b = bytes[p].toInt() and 0xFF
            p++
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            // 防御：畸形输入里 shift 会无限增长，5 段（35 位）后按非法处理
            if (shift > 63) return result to p
        }
        return result to p
    }

    /**
     * 解析全部字段（遇到非法结构即停止，返回已解析部分）。
     *
     * **未知 wire type 一律 break**，但 1/2/5 都正确跳过——见文件头注释。
     */
    fun readFields(bytes: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var pos = 0
        while (pos < bytes.size) {
            val (tagV, p1) = readVarint(bytes, pos)
            pos = p1
            val fieldNo = (tagV ushr 3).toInt()
            val wireType = (tagV and 0x7).toInt()

            when (wireType) {
                0 -> {
                    val (v, p2) = readVarint(bytes, pos)
                    pos = p2
                    out += Field(fieldNo, 0, varint = v)
                }
                1 -> {
                    if (pos + 8 > bytes.size) return out
                    out += Field(fieldNo, 1, fixed64 = bytes.copyOfRange(pos, pos + 8))
                    pos += 8
                }
                2 -> {
                    val (sizeV, p2) = readVarint(bytes, pos)
                    pos = p2
                    val size = sizeV.toInt()
                    if (size < 0 || pos + size > bytes.size) return out
                    out += Field(fieldNo, 2, bytes = bytes.copyOfRange(pos, pos + size))
                    pos += size
                }
                5 -> {
                    if (pos + 4 > bytes.size) return out
                    pos += 4   // fixed32：跳过，不保留（本项目不用该字段值）
                }
                else -> return out
            }
        }
        return out
    }

    /** 取某字段号的首个长度前缀字段字节 */
    fun bytesOf(fields: List<Field>, fieldNo: Int): ByteArray =
        fields.firstOrNull { it.fieldNo == fieldNo && it.wireType == 2 }?.bytes ?: ByteArray(0)

    /** 取某字段号的首个 varint 值（缺省 -1） */
    fun varintOf(fields: List<Field>, fieldNo: Int): Long =
        fields.firstOrNull { it.fieldNo == fieldNo && it.wireType == 0 }?.varint ?: -1L

    /** 取某字段号的全部长度前缀字段字节（repeated） */
    fun allBytesOf(fields: List<Field>, fieldNo: Int): List<ByteArray> =
        fields.filter { it.fieldNo == fieldNo && it.wireType == 2 }.map { it.bytes }

    /** 取某字段号的文本（UTF-8）；无该字段返回空串 */
    fun textOf(fields: List<Field>, fieldNo: Int): String {
        val b = bytesOf(fields, fieldNo)
        return if (b.isEmpty()) "" else Digest.utf8String(b)
    }
}
