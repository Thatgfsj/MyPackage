package com.thatgfsj.mypackage.data

import android.util.Base64
import com.thatgfsj.mypackage.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// ================= JSON 模型 =================

@Serializable
data class StationDTO(
    val n: String = "",
    val p: String = Platform.PDD.name,
    val l: String = "",
    val r: String = "",
    val c: String = "",
    /** 压缩后的站点图片 base64（JPEG），随分享一起传输 */
    val img: String = "",
    val o: Int = 0
)

@Serializable
data class StationPayload(
    val v: Int = 1,
    val s: List<StationDTO> = emptyList()
)

/** 旧版顺序分片格式（仅保留接收兼容，新分享不再产生） */
@Serializable
data class ChunkPayload(
    val v: Int = 1,
    val t: Int,
    val i: Int,
    val c: Long = 0,
    val d: String
)

object ShareCodec {

    /** 单个二维码承载的原始数据上限（字节）。控制在 800 内保证二维码稀疏、易于扫描 */
    const val QR_BYTE_LIMIT = 800

    /** 喷泉码单块目标大小（字节）。每帧 ≈ 300 字节（base64 后 ~304 字符），QR v8-9，老设备也易扫 */
    const val FRAME_BLOCK_SIZE = 220

    /** 每轮冗余帧比例：+50%，保证多轮累积快速收敛（大 k 单轮也基本够） */
    const val FRAME_OVERHEAD = 0.5

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toDto(s: StationEntity) = StationDTO(
        n = s.name, p = s.platform, l = s.rawLink,
        r = s.lockerRange, c = s.customPackage,
        img = ImageUtils.toSmallBase64(s.imagePath),
        o = s.sortOrder
    )

    /** 用于 JSON 导出/备份：图片用原图，保证清晰度（体积会更大，仅文件路径可用） */
    fun toDtoFull(s: StationEntity) = StationDTO(
        n = s.name, p = s.platform, l = s.rawLink,
        r = s.lockerRange, c = s.customPackage,
        img = ImageUtils.toFullBase64(s.imagePath),
        o = s.sortOrder
    )

    /** 直接导出为完整 JSON（用于文件备份/网络导入，图片以原图 base64 内嵌，保留清晰度） */
    fun encodeFull(stations: List<StationEntity>): String =
        json.encodeToString(StationPayload(s = stations.map(::toDtoFull)))

    /** 供二维码分享使用：图片压缩为小图 base64，控制二维码体积 */
    fun encodeSmall(stations: List<StationEntity>): String =
        json.encodeToString(StationPayload(s = stations.map(::toDto)))

    /**
     * 编码为二维码内容：
     * JSON ≤ 800 字节 → 单个原始 JSON；
     * 超过 → gzip 压缩后按喷泉码（LT）分帧，每帧 ≈ 544 字节，
     * 任意顺序、任意轮次扫描，凑够唯一帧即可解码（详情见 FountainPrepared）。
     */
    suspend fun encode(stations: List<StationEntity>): List<String> =
        withContext(Dispatchers.IO) {
            val text = encodeFull(stations)
            if (text.toByteArray(Charsets.UTF_8).size <= QR_BYTE_LIMIT) {
                listOf(text)
            } else {
                val prep = prepareFountain(text)
                (0 until prep.frameCount).map { frameText(prep, it, 0) }
            }
        }

    // ================= 喷泉码（LT）编帧 =================

    class FountainPrepared internal constructor(
        val k: Int,
        val origLen: Int,
        val blockLen: Int,
        internal val data: ByteArray,
        val frameCount: Int
    )

    /** gzip 压缩并切分为 k 个数据块，准备喷泉编码（k 上限 511，9 位可表达） */
    fun prepareFountain(text: String): FountainPrepared {
        val gz = gzip(text.toByteArray(Charsets.UTF_8))
        var k = ((gz.size + FRAME_BLOCK_SIZE - 1) / FRAME_BLOCK_SIZE).coerceAtLeast(1).coerceAtMost(511)
        val blockLen = (gz.size + k - 1) / k
        val data = gz.copyOf(blockLen * k)
        val frameCount = k + maxOf(3, (k * FRAME_OVERHEAD).toInt())
        return FountainPrepared(k, gz.size, blockLen, data, frameCount)
    }

    /** 帧二进制布局：[k(2)][origLen(3)][seed(4)] 9 字节头 + 数据；k/seed 用足位，长轮播不截断 */
    fun frameText(p: FountainPrepared, nonce: Int, round: Int): String {
        val seed = round * p.frameCount + nonce
        val indices = indicesFor(seed, p.k)
        val xor = ByteArray(p.blockLen)
        for (i in indices) {
            val blk = p.data.copyOfRange(i * p.blockLen, (i + 1) * p.blockLen)
            for (j in xor.indices) xor[j] = (xor[j].toInt() xor blk[j].toInt()).toByte()
        }
        val frame = ByteArray(9 + xor.size)
        frame[0] = (p.k shr 8).toByte()
        frame[1] = p.k.toByte()
        frame[2] = (p.origLen shr 16).toByte()
        frame[3] = (p.origLen shr 8).toByte()
        frame[4] = p.origLen.toByte()
        frame[5] = (seed shr 24).toByte()
        frame[6] = (seed shr 16).toByte()
        frame[7] = (seed shr 8).toByte()
        frame[8] = seed.toByte()
        xor.copyInto(frame, 9)
        return Base64.encodeToString(frame, Base64.NO_WRAP)
    }

    /** 由 seed 确定 LT 度数与数据块索引（编解码两端一致） */
    fun indicesFor(seed: Int, k: Int): List<Int> {
        if (k == 1) return listOf(0)
        val rnd = Random(seed.toLong() * 2654435761L)
        val d = degree(k, rnd)
        val set = LinkedHashSet<Int>()
        while (set.size < d) set.add(rnd.nextInt(k))
        return set.toList()
    }

    /** 鲁棒孤波分布，带低度数保底（约 1/3 帧为度 1/2，保证解码立即启动） */
    private fun degree(k: Int, rnd: Random): Int {
        val x0 = rnd.nextDouble()
        if (x0 < 0.18) return 1
        if (x0 < 0.34) return 2
        val c = 0.05
        val delta = 0.05
        val r = c * kotlin.math.sqrt(k.toDouble()) * kotlin.math.ln(k.toDouble() / delta)
        val probs = DoubleArray(k + 1)
        var z = 0.0
        for (i in 1..k) {
            var p = if (i == 1) 1.0 / k else 1.0 / (i * (i - 1))
            if (i <= r.toInt()) p += r / (i * k)
            probs[i] = p
            z += p
        }
        val x = rnd.nextDouble() * z
        var acc = 0.0
        for (i in 1..k) {
            acc += probs[i]
            if (x <= acc) return i
        }
        return k
    }

    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    // ================= 接收端（喷泉解码，剥离法） =================

    class FountainAssembler(val k: Int, val origLen: Int) {
        private val blocks = arrayOfNulls<ByteArray>(k)
        private val pending = mutableListOf<Pair<MutableSet<Int>, ByteArray>>()
        private val seenSeeds = HashSet<Int>()

        var recovered = 0
            private set

        fun isSeen(seed: Int): Boolean = seed in seenSeeds

        /** @return true = 产生了新进展（恢复出新块） */
        fun addFrame(frame: ByteArray): Boolean {
            if (frame.size < 10) return false
            val seed = seedOf(frame)
            if (!seenSeeds.add(seed)) return false
            val payload = frame.copyOfRange(9, frame.size)
            val indices = indicesFor(seed, k)
            var xor = payload
            val unknown = indices.toMutableSet()
            for (i in indices) blocks[i]?.let { known ->
                for (j in known.indices) xor[j] = (xor[j].toInt() xor known[j].toInt()).toByte()
                unknown.remove(i)
            }
            pending.add(unknown to xor)
            return peel()
        }

        private fun peel(): Boolean {
            var progressed = false
            var again = true
            while (again) {
                again = false
                val it = pending.iterator()
                while (it.hasNext()) {
                    val (unknown, xor) = it.next()
                    val unk = unknown.toMutableSet()
                    for (i in unknown) blocks[i]?.let { known ->
                        for (j in known.indices) xor[j] = (xor[j].toInt() xor known[j].toInt()).toByte()
                        unk.remove(i)
                    }
                    when {
                        unk.isEmpty() -> { it.remove(); again = true; progressed = true }
                        unk.size == 1 -> {
                            val i = unk.first()
                            blocks[i] = xor.copyOf()
                            recovered++
                            it.remove()
                            again = true; progressed = true
                        }
                        else -> { unknown.clear(); unknown.addAll(unk) }
                    }
                }
            }
            return progressed
        }

        fun isComplete(): Boolean = recovered == k

        fun combine(): String? {
            if (recovered != k) return null
            val all = ByteArray(origLen)
            var off = 0
            for (b in blocks) {
                val n = minOf(b!!.size, origLen - off)
                b.copyInto(all, off, 0, n)
                off += n
                if (off >= origLen) break
            }
            return try {
                String(gunzip(all), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun frameInfo(frame: ByteArray): Triple<Int, Int, Int>? {
        if (frame.size < 10) return null
        val k = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
        val origLen = ((frame[2].toInt() and 0xFF) shl 16) or ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        val seed = ((frame[5].toInt() and 0xFF) shl 24) or ((frame[6].toInt() and 0xFF) shl 16) or
            ((frame[7].toInt() and 0xFF) shl 8) or (frame[8].toInt() and 0xFF)
        return Triple(k, origLen, seed)
    }

    fun decodeFrame(text: String): ByteArray? = try {
        Base64.decode(text.trim(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    private fun seedOf(frame: ByteArray): Int =
        ((frame[5].toInt() and 0xFF) shl 24) or ((frame[6].toInt() and 0xFF) shl 16) or
            ((frame[7].toInt() and 0xFF) shl 8) or (frame[8].toInt() and 0xFF)

    // ================= 旧版顺序分片（仅接收兼容） =================

    fun tryDecodeFull(text: String): StationPayload? = try {
        val t = text.trim()
        if (t.startsWith("{") && t.contains("\"s\"")) json.decodeFromString<StationPayload>(t) else null
    } catch (e: Exception) {
        null
    }

    fun tryDecodeChunk(text: String): ChunkPayload? = try {
        val t = text.trim()
        if (t.startsWith("{") && t.contains("\"d\"")) json.decodeFromString<ChunkPayload>(t) else null
    } catch (e: Exception) {
        null
    }

    /** 合并旧版顺序分片；页数不符、CRC 校验失败或 JSON 损坏返回 null */
    fun assemble(chunks: Collection<ChunkPayload>): StationPayload? {
        if (chunks.isEmpty()) return null
        val total = chunks.first().t
        if (total <= 0) return null
        val distinct = chunks.distinctBy { it.i }
        if (distinct.size != total || distinct.any { it.i < 0 || it.i >= total || it.t != total }) return null
        val crcSet = distinct.map { it.c }.toSet()
        if (crcSet.size != 1) return null
        return try {
            val b64 = distinct.sortedBy { it.i }.joinToString("") { it.d }
            if (crc32(b64) != crcSet.first()) return null
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            json.decodeFromString<StationPayload>(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private fun crc32(s: String): Long =
        java.util.zip.CRC32().apply { update(s.toByteArray(Charsets.US_ASCII)) }.value
}

/** 接收端：旧版顺序分片收集器（兼容老版本分享） */
class ChunkAssembler {
    private val parts = mutableMapOf<Int, ChunkPayload>()

    fun add(chunk: ChunkPayload): Collection<ChunkPayload>? {
        parts[chunk.i] = chunk
        val total = chunk.t
        val usable = parts.values.filter { it.t == total }
        return if (usable.size >= total) usable else null
    }

    fun count(): Int = parts.size

    fun has(index: Int): Boolean = parts.containsKey(index)

    fun received(): Set<Int> = parts.keys.toSortedSet()

    fun reset() = parts.clear()
}
