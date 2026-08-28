package com.thatgfsj.mypackage.data

import android.content.Context
import com.thatgfsj.mypackage.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StationRepository(private val dao: StationDao, private val appContext: Context) {

    fun observeAll(): Flow<List<StationEntity>> = dao.observeAll()

    suspend fun getAll(): List<StationEntity> = dao.getAll()

    suspend fun getById(id: Long): StationEntity? = dao.getById(id)

    suspend fun save(station: StationEntity): StationEntity {
        return if (station.id == 0L) {
            val nextSort = (dao.maxSortOrder() ?: 0) + 1
            val id = dao.insert(station.copy(sortOrder = nextSort))
            station.copy(id = id, sortOrder = nextSort)
        } else {
            dao.update(station)
            station
        }
    }

    suspend fun delete(station: StationEntity) = dao.delete(station)

    /** 快捷改名：只改名称，其他字段与位置不变 */
    suspend fun rename(id: Long, newName: String) {
        val s = dao.getById(id) ?: return
        val name = newName.trim()
        if (name.isEmpty() || name == s.name) return
        dao.update(s.copy(name = name))
    }

    /** 快捷换图：更新图片路径并清理旧图片文件 */
    suspend fun updateImage(id: Long, newPath: String) {
        val s = dao.getById(id) ?: return
        val old = s.imagePath
        if (newPath == old) return
        dao.update(s.copy(imagePath = newPath))
        if (old.isNotBlank()) ImageUtils.deleteImage(old)
    }

    suspend fun moveBy(id: Long, delta: Int) {
        val list = dao.getAll().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.add(to, list.removeAt(from))
        persistOrder(list)
    }

    /** 移动到指定位置（0 = 最前面，Int.MAX_VALUE = 最后面） */
    suspend fun moveTo(id: Long, index: Int) {
        val list = dao.getAll().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = index.coerceIn(0, list.lastIndex)
        if (from == to) return
        list.add(to, list.removeAt(from))
        persistOrder(list)
    }

    /** 从分享/备份的 JSON 导入：还原 base64 图片文件后逐个入库（追加到末尾） */
    suspend fun importPayload(payload: StationPayload) = withContext(Dispatchers.IO) {
        payload.s.forEach { dto ->
            val imagePath = if (dto.img.isNotBlank()) {
                ImageUtils.saveJpegFromBase64(appContext, dto.img) ?: ""
            } else ""
            save(
                StationEntity(
                    name = dto.n.ifBlank { "未命名驿站" },
                    platform = dto.p,
                    rawLink = dto.l,
                    lockerRange = dto.r,
                    customPackage = dto.c,
                    imagePath = imagePath
                )
            )
        }
    }

    private suspend fun persistOrder(list: List<StationEntity>) {
        list.forEachIndexed { index, s ->
            if (s.sortOrder != index + 1) dao.update(s.copy(sortOrder = index + 1))
        }
    }

    companion object {
        @Volatile
        private var instance: StationRepository? = null

        fun get(context: Context): StationRepository = instance ?: synchronized(this) {
            instance ?: StationRepository(
                AppDatabase.get(context).stationDao(),
                context.applicationContext
            ).also { instance = it }
        }
    }
}
