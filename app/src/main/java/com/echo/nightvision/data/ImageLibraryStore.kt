package com.echo.nightvision.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.echo.nightvision.processing.PixelImage
import java.io.File
import java.util.UUID

data class ImageAsset(
    val id: String,
    val title: String,
    val sourceType: String,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val imagePath: String,
    val thumbnailPath: String,
    val favorite: Boolean
)

object ImageLibraryStore {
    private const val DATABASE_NAME = "nightvision-library.db"
    private const val DATABASE_VERSION = 1
    private const val TABLE = "image_assets"

    private class LibraryDatabase(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                    "id TEXT PRIMARY KEY NOT NULL," +
                    "title TEXT NOT NULL," +
                    "source_type TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "width INTEGER NOT NULL," +
                    "height INTEGER NOT NULL," +
                    "image_path TEXT NOT NULL," +
                    "thumbnail_path TEXT NOT NULL," +
                    "favorite INTEGER NOT NULL DEFAULT 0" +
                    ")"
            )
            db.execSQL("CREATE INDEX idx_image_assets_created ON " + TABLE + "(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 1) onCreate(db)
        }
    }

    fun list(context: Context): List<ImageAsset> {
        val db = LibraryDatabase(context).readableDatabase
        val cursor = db.query(
            TABLE,
            arrayOf("id", "title", "source_type", "created_at", "width", "height", "image_path", "thumbnail_path", "favorite"),
            null, null, null, null,
            "favorite DESC, created_at DESC"
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        ImageAsset(
                            id = it.getString(0),
                            title = it.getString(1),
                            sourceType = it.getString(2),
                            timestamp = it.getLong(3),
                            width = it.getInt(4),
                            height = it.getInt(5),
                            imagePath = it.getString(6),
                            thumbnailPath = it.getString(7),
                            favorite = it.getInt(8) == 1
                        )
                    )
                }
            }.filter { File(it.imagePath).exists() && File(it.thumbnailPath).exists() }
        }.also { db.close() }
    }

    fun save(context: Context, title: String, sourceType: String, image: PixelImage, sourceUri: Uri? = null): ImageAsset {
        val folder = File(context.filesDir, "nightvision-library")
        check(folder.exists() || folder.mkdirs()) { "无法创建图片库目录。" }
        val id = UUID.randomUUID().toString()
        val extension = sourceUri?.let { uri ->
            when (context.contentResolver.getType(uri)?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
        } ?: "jpg"
        val imageFile = File(folder, id + "." + extension)
        val thumbnailFile = File(folder, id + "-thumb.jpg")
        try {
            if (sourceUri != null) {
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: error("无法读取原始图片文件。")
                input.use { source -> imageFile.outputStream().use { destination -> source.copyTo(destination) } }
            } else {
                writeJpeg(image, imageFile, 94, 0)
            }
            writeJpeg(image, thumbnailFile, 82, 360)
        } catch (error: Exception) {
            imageFile.delete()
            thumbnailFile.delete()
            throw error
        }
        val asset = ImageAsset(
            id = id,
            title = title.ifBlank { "未命名图片" },
            sourceType = sourceType,
            timestamp = System.currentTimeMillis(),
            width = image.width,
            height = image.height,
            imagePath = imageFile.absolutePath,
            thumbnailPath = thumbnailFile.absolutePath,
            favorite = false
        )
        val values = ContentValues().apply {
            put("id", asset.id)
            put("title", asset.title)
            put("source_type", asset.sourceType)
            put("created_at", asset.timestamp)
            put("width", asset.width)
            put("height", asset.height)
            put("image_path", asset.imagePath)
            put("thumbnail_path", asset.thumbnailPath)
            put("favorite", 0)
        }
        try {
            val db = LibraryDatabase(context).writableDatabase
            db.insertOrThrow(TABLE, null, values)
            db.close()
        } catch (error: Exception) {
            imageFile.delete()
            thumbnailFile.delete()
            throw error
        }
        return asset
    }

    fun load(asset: ImageAsset): PixelImage? {
        val file = File(asset.imagePath)
        if (!file.exists()) return null
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            var sample = 1
            val maxDimension = maxOf(info.size.width, info.size.height)
            while (maxDimension / sample > 1800) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PixelImage(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean) {
        val db = LibraryDatabase(context).writableDatabase
        db.update(TABLE, ContentValues().apply { put("favorite", if (favorite) 1 else 0) }, "id = ?", arrayOf(id))
        db.close()
    }

    fun delete(context: Context, asset: ImageAsset) {
        val db = LibraryDatabase(context).writableDatabase
        db.delete(TABLE, "id = ?", arrayOf(asset.id))
        db.close()
        File(asset.imagePath).delete()
        File(asset.thumbnailPath).delete()
    }

    private fun writeJpeg(image: PixelImage, file: File, quality: Int, maxDimension: Int) {
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        val outputBitmap = if (maxDimension > 0 && maxOf(image.width, image.height) > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(image.width, image.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        try {
            file.outputStream().use { stream ->
                check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "图片文件写入失败。" }
            }
        } finally {
            if (outputBitmap !== bitmap) outputBitmap.recycle()
            bitmap.recycle()
        }
    }
}
