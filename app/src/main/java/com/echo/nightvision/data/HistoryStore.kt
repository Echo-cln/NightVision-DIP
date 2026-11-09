package com.echo.nightvision.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.echo.nightvision.processing.PixelImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HistoryItem(
    val id: String,
    val title: String,
    val mode: String,
    val timestamp: Long,
    val originalPath: String,
    val resultPath: String,
    val summary: String
)

object HistoryStore {
    private const val PREFS = "nightvision_history"
    private const val KEY = "records"
    private const val LIMIT = 20

    fun list(context: Context): List<HistoryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(HistoryItem(
                        o.getString("id"), o.getString("title"), o.getString("mode"),
                        o.getLong("timestamp"), o.getString("originalPath"),
                        o.getString("resultPath"), o.optString("summary")
                    ))
                }
            }.filter { File(it.originalPath).exists() && File(it.resultPath).exists() }
        } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, title: String, mode: String, original: PixelImage, result: PixelImage, summary: String): HistoryItem {
        val folder = File(context.filesDir, "nightvision-history")
        check(folder.exists() || folder.mkdirs()) { "无法创建本地记录目录" }
        val id = UUID.randomUUID().toString()
        val originalFile = File(folder, id + "-original.jpg")
        val resultFile = File(folder, id + "-result.jpg")
        write(original, originalFile)
        write(result, resultFile)
        val item = HistoryItem(id, title, mode, System.currentTimeMillis(), originalFile.absolutePath, resultFile.absolutePath, summary)
        val previous = list(context)
        val retained = (listOf(item) + previous).take(LIMIT)
        val keep = retained.map { it.id }.toSet()
        previous.filter { it.id !in keep }.forEach(::removeFiles)
        persist(context, retained)
        return item
    }

    fun load(item: HistoryItem): Pair<PixelImage, PixelImage>? {
        val a = BitmapFactory.decodeFile(item.originalPath) ?: return null
        val b = BitmapFactory.decodeFile(item.resultPath) ?: return null
        return pixels(a) to pixels(b)
    }

    fun delete(context: Context, id: String) {
        val current = list(context)
        current.firstOrNull { it.id == id }?.let(::removeFiles)
        persist(context, current.filterNot { it.id == id })
    }

    fun clear(context: Context) {
        list(context).forEach(::removeFiles)
        persist(context, emptyList())
    }

    private fun pixels(bitmap: Bitmap): PixelImage {
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PixelImage(bitmap.width, bitmap.height, data)
    }

    private fun write(image: PixelImage, file: File) {
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "处理记录写入失败" } }
        bitmap.recycle()
    }

    private fun removeFiles(item: HistoryItem) {
        File(item.originalPath).delete()
        File(item.resultPath).delete()
    }

    private fun persist(context: Context, items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id).put("title", item.title).put("mode", item.mode)
                .put("timestamp", item.timestamp).put("originalPath", item.originalPath)
                .put("resultPath", item.resultPath).put("summary", item.summary))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
