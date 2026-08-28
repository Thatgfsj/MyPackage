package com.thatgfsj.mypackage.data

import android.util.Base64
import com.thatgfsj.mypackage.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

@Serializable
data class ChunkPayload(
    val v: Int = 1,
    val t: Int,
    val i: Int,
    val d: String
)

object ShareCodec {

    /** 单个二维码承载的原始数据上限（字节） */
    const val QR_BYTE_LIMIT = 1800

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

    /** 直接导出为完整 JSON（用于文件备份，图片以 base64 内嵌） */
    fun encodeFull(stations: List<StationEntity>): String =
        json.encodeToString(StationPayload(s = stations.map(::toDto)))

    /**
     * 编码为 1~N 个二维码内容：
     * 不超过 1800 字节 → 单个原始 JSON；
     * 超过 → 整体 base64 后按 1600 字符切片，每片带 {t:总数, i:序号} 头，
     * 接收端按序合并、base64 解码还原 JSON。
     */
    suspend fun encode(stations: List<StationEntity>): List<String> =
        withContext(Dispatchers.IO) {
            val text = encodeFull(stations)
            if (text.toByteArray(Charsets.UTF_8).size <= QR_BYTE_LIMIT) {
                listOf(text)
            } else {
                val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val parts = b64.chunked(CHUNK_CHARS)
                parts.mapIndexed { i, p ->
                    json.encodeToString(ChunkPayload(v = 1, t = parts.size, i = i, d = p))
                }
            }
        }

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

    /** 合并全部分片；分片不完整或数据损坏返回 null */
    fun assemble(chunks: Collection<ChunkPayload>): StationPayload? {
        if (chunks.isEmpty()) return null
        val total = chunks.first().t
        if (total <= 0) return null
        val distinct = chunks.distinctBy { it.i }
        if (distinct.size != total || distinct.any { it.i < 0 || it.i >= total || it.t != total }) return null
        return try {
            val b64 = distinct.sortedBy { it.i }.joinToString("") { it.d }
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            json.decodeFromString<StationPayload>(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private const val CHUNK_CHARS = 1600
}

/** 接收端跨多次扫码的分片收集器 */
class ChunkAssembler {
    private val parts = mutableMapOf<Int, ChunkPayload>()

    fun add(chunk: ChunkPayload): Collection<ChunkPayload>? {
        parts[chunk.i] = chunk
        val total = chunk.t
        val usable = parts.values.filter { it.t == total }
        return if (usable.size >= total) usable else null
    }

    fun count(): Int = parts.size

    /** 已收到的分片序号（升序） */
    fun received(): Set<Int> = parts.keys.toSortedSet()

    fun reset() = parts.clear()
}
