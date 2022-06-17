package com.idir.simplecropviewcompose.util

import android.annotation.SuppressLint
import android.content.*
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.opengl.GLES10
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import java.io.*
import com.idir.simplecropviewcompose.util.Logger.*

class Utils {
    companion object{


    private val TAG = Utils::class.java.simpleName
    private val SIZE_DEFAULT = 2048
    private val SIZE_LIMIT = 4096
    var sInputImageWidth = 0
    var sInputImageHeight = 0

    /**
     * Copy EXIF info to new file
     *
     * =========================================
     *
     * NOTE: PNG cannot not have EXIF info.
     *
     * source: JPEG, save: JPEG
     * copies all EXIF data
     *
     * source: JPEG, save: PNG
     * saves no EXIF data
     *
     * source: PNG, save: JPEG
     * saves only width and height EXIF data
     *
     * source: PNG, save: PNG
     * saves no EXIF data
     *
     * =========================================
     */
    fun copyExifInfo(
        context: Context, sourceUri: Uri?, saveUri: Uri?, outputWidth: Int,
        outputHeight: Int
    ) {
        if (sourceUri == null || saveUri == null) return
        try {
            val sourceFile: File? = Utils.getFileFromUri(context, sourceUri)
            val saveFile: File? = Utils.getFileFromUri(context, saveUri)
            if (sourceFile == null || saveFile == null) {
                return
            }
            val sourcePath: String = sourceFile.getAbsolutePath()
            val savePath: String = saveFile.getAbsolutePath()
            val sourceExif = ExifInterface(sourcePath)
            val tags: MutableList<String> = ArrayList()
            tags.add(ExifInterface.TAG_DATETIME)
            tags.add(ExifInterface.TAG_FLASH)
            tags.add(ExifInterface.TAG_FOCAL_LENGTH)
            tags.add(ExifInterface.TAG_GPS_ALTITUDE)
            tags.add(ExifInterface.TAG_GPS_ALTITUDE_REF)
            tags.add(ExifInterface.TAG_GPS_DATESTAMP)
            tags.add(ExifInterface.TAG_GPS_LATITUDE)
            tags.add(ExifInterface.TAG_GPS_LATITUDE_REF)
            tags.add(ExifInterface.TAG_GPS_LONGITUDE)
            tags.add(ExifInterface.TAG_GPS_LONGITUDE_REF)
            tags.add(ExifInterface.TAG_GPS_PROCESSING_METHOD)
            tags.add(ExifInterface.TAG_GPS_TIMESTAMP)
            tags.add(ExifInterface.TAG_MAKE)
            tags.add(ExifInterface.TAG_MODEL)
            tags.add(ExifInterface.TAG_WHITE_BALANCE)
            if (Build.VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
                tags.add(ExifInterface.TAG_EXPOSURE_TIME)
                tags.add(ExifInterface.TAG_APERTURE)
                tags.add(ExifInterface.TAG_ISO)
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                tags.add(ExifInterface.TAG_DATETIME_DIGITIZED)
                tags.add(ExifInterface.TAG_SUBSEC_TIME)
                tags.add(ExifInterface.TAG_SUBSEC_TIME_DIG)
                tags.add(ExifInterface.TAG_SUBSEC_TIME_ORIG)
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
                tags.add(ExifInterface.TAG_F_NUMBER)
                tags.add(ExifInterface.TAG_ISO_SPEED_RATINGS)
                tags.add(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED)
                tags.add(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
            }
            val saveExif = ExifInterface(savePath)
            var value: String?
            for (tag in tags) {
                value = sourceExif.getAttribute(tag)
                if (!TextUtils.isEmpty(value)) {
                    saveExif.setAttribute(tag, value)
                }
            }
            saveExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, outputWidth.toString())
            saveExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, outputHeight.toString())
            saveExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED.toString()
            )
            saveExif.saveAttributes()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getExifRotation(file: File?): Int {
        if (file == null) return 0
        try {
            val exif = ExifInterface(file.getAbsolutePath())
            return getRotateDegreeFromOrientation(
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            )
        } catch (e: IOException) {
            Logger.e("An error occurred while getting the exif data: " + e.message, e)
        }
        return 0
    }

    fun getExifRotation(context: Context, uri: Uri): Int {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.Images.ImageColumns.ORIENTATION)
        return try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null)
            if (cursor == null || !cursor.moveToFirst()) {
                0
            } else cursor.getInt(0)
        } catch (ignored: RuntimeException) {
            0
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    fun getExifOrientation(context: Context, uri: Uri): Int {
        val authority: String = uri.getAuthority()!!.toLowerCase()
        val orientation: Int
        orientation = if (authority.endsWith("media")) {
            getExifRotation(context, uri)
        } else {
            getExifRotation(getFileFromUri(context, uri))
        }
        return orientation
    }

    fun getRotateDegreeFromOrientation(orientation: Int): Int {
        var degree = 0
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
            ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
            ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
            else -> {}
        }
        return degree
    }

    fun getMatrixFromExifOrientation(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_UNDEFINED -> {}
            ExifInterface.ORIENTATION_NORMAL -> {}
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1.0f, 1.0f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180.0f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1.0f, -1.0f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90.0f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90.0f)
                matrix.postScale(1.0f, -1.0f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90.0f)
                matrix.postScale(1.0f, -1.0f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90.0f)
        }
        return matrix
    }

    fun getExifOrientationFromAngle(angle: Int): Int {
        val normalizedAngle = angle % 360
        return when (normalizedAngle) {
            0 -> ExifInterface.ORIENTATION_NORMAL
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }

    @SuppressLint("WrongConstant")
    fun ensureUriPermission(context: Context, intent: Intent): Uri? {
        val uri: Uri? = intent.data
        val takeFlags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.getContentResolver().takePersistableUriPermission(uri!!, takeFlags)
        return uri
    }

    /**
     * Get image file from uri
     *
     * @param context The context
     * @param uri The Uri of the image
     * @return Image file
     */
    fun getFileFromUri(
        context: Context,
        uri: Uri
    ): File? {
        var filePath: String? = null
        val isKitkat = Build.VERSION.SDK_INT >= VERSION_CODES.KITKAT
        // DocumentProvider
        if (isKitkat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    filePath = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                // String "id" may not represent a valid Long type data, it may equals to
                // something like "raw:/storage/emulated/0/Download/some_file" instead.
                // Doing a check before passing the "id" to Long.valueOf(String) would be much safer.
                filePath = if (RawDocumentsHelper.isRawDocId(id)) {
                    RawDocumentsHelper.getAbsoluteFilePath(id)
                } else {
                    val contentUri: Uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        java.lang.Long.valueOf(id)
                    )
                    getDataColumn(context, contentUri, null, null)
                }
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                lateinit var contentUri: Uri
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                filePath = getDataColumn(context, contentUri, selection, selectionArgs)
            } else if (isGoogleDriveDocument(uri)) {
                return getGoogleDriveFile(context, uri)
            }
        } else if ("content".equals(uri.getScheme(), ignoreCase = true)) {
            filePath = if (isGooglePhotosUri(uri)) {
                uri.getLastPathSegment()
            } else {
                getDataColumn(context, uri, null, null)
            }
        } else if ("file".equals(uri.getScheme(), ignoreCase = true)) {
            filePath = uri.getPath()
        }
        return filePath?.let { File(it) }
    }

    // A copy of com.android.providers.downloads.RawDocumentsHelper since it is invisibility.
    object RawDocumentsHelper {
        const val RAW_PREFIX = "raw:"
        fun isRawDocId(docId: String?): Boolean {
            return docId != null && docId.startsWith(RAW_PREFIX)
        }

        fun getDocIdForFile(file: File): String {
            return RAW_PREFIX + file.getAbsolutePath()
        }

        fun getAbsoluteFilePath(rawDocumentId: String): String {
            return rawDocumentId.substring(RAW_PREFIX.length)
        }
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    fun getDataColumn(
        context: Context, uri: Uri, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME
        )
        try {
            cursor =
                context.getContentResolver().query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex: Int = if (uri.toString()
                        .startsWith("content://com.google.android.gallery3d")
                ) cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME) else cursor.getColumnIndex(
                    MediaStore.MediaColumns.DATA
                )
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex)
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check
     * @return Whether the Uri authority is Google Drive.
     */
    fun isGoogleDriveDocument(uri: Uri): Boolean {
        return "com.google.android.apps.docs.storage" == uri.getAuthority()
    }

    /**
     * @param context The context
     * @param uri The Uri of Google Drive file
     * @return Google Drive file
     */
    private fun getGoogleDriveFile(context: Context, uri: Uri?): File? {
        if (uri == null) return null
        var input: FileInputStream? = null
        var output: FileOutputStream? = null
        val filePath: String = File(context.getCacheDir(), "tmp").getAbsolutePath()
        try {
            val pfd: ParcelFileDescriptor =
                context.getContentResolver().openFileDescriptor(uri, "r")
                    ?: return null
            val fd: FileDescriptor = pfd.fileDescriptor
            input = FileInputStream(fd)
            output = FileOutputStream(filePath)
            var read: Int
            val bytes = ByteArray(4096)
            while (input.read(bytes).also { read = it } != -1) {
                output.write(bytes, 0, read)
            }
            return File(filePath)
        } catch (ignored: IOException) {
        } finally {
            closeQuietly(input)
            closeQuietly(output)
        }
        return null
    }

    fun decodeSampledBitmapFromUri(context: Context, sourceUri: Uri, requestSize: Int): Bitmap? {
        var stream: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            stream = context.getContentResolver().openInputStream(sourceUri)
            if (stream != null) {
                val options = BitmapFactory.Options()
                options.inSampleSize = Utils.calculateInSampleSize(context, sourceUri, requestSize)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: FileNotFoundException) {
            Logger.e(e.message)
        } finally {
            try {
                if (stream != null) {
                    stream.close()
                }
            } catch (e: IOException) {
                Logger.e(e.message)
            }
        }
        return bitmap
    }

    fun calculateInSampleSize(context: Context, sourceUri: Uri?, requestSize: Int): Int {
        var `is`: InputStream? = null
        // check image size
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        try {
            `is` = context.getContentResolver().openInputStream(sourceUri!!)
            BitmapFactory.decodeStream(`is`, null, options)
        } catch (ignored: FileNotFoundException) {
        } finally {
            closeQuietly(`is`)
        }
        var inSampleSize = 1
        sInputImageWidth = options.outWidth
        sInputImageHeight = options.outHeight
        while (options.outWidth / inSampleSize > requestSize
            || options.outHeight / inSampleSize > requestSize
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun getScaledBitmapForHeight(bitmap: Bitmap, outHeight: Int): Bitmap? {
        val currentWidth = bitmap.width.toFloat()
        val currentHeight = bitmap.height.toFloat()
        val ratio = currentWidth / currentHeight
        val outWidth = Math.round(outHeight * ratio)
        return getScaledBitmap(bitmap, outWidth, outHeight)
    }

    fun getScaledBitmapForWidth(bitmap: Bitmap, outWidth: Int): Bitmap {
        val currentWidth = bitmap.width.toFloat()
        val currentHeight = bitmap.height.toFloat()
        val ratio = currentWidth / currentHeight
        val outHeight = Math.round(outWidth / ratio)
        return getScaledBitmap(bitmap, outWidth, outHeight)
    }

    fun getScaledBitmap(bitmap: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val currentWidth = bitmap.width
        val currentHeight = bitmap.height
        val scaleMatrix = Matrix()
        scaleMatrix.postScale(
            outWidth.toFloat() / currentWidth.toFloat(),
            outHeight.toFloat() / currentHeight.toFloat()
        )
        return createBitmap(bitmap, 0, 0, currentWidth, currentHeight, scaleMatrix, true)
    }

    fun getMaxSize(): Int {
        var maxSize = SIZE_DEFAULT
        val arr = IntArray(1)
        GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, arr, 0)
        if (arr[0] > 0) {
            maxSize = Math.min(arr[0], SIZE_LIMIT)
        }
        return maxSize
    }

    fun closeQuietly(closeable: Closeable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (ignored: Throwable) {
        }
    }

    fun updateGalleryInfo(context: Context, uri: Uri) {
        if (ContentResolver.SCHEME_CONTENT != uri.getScheme()) {
            return
        }
        val values = ContentValues()
        val file: File? = getFileFromUri(context, uri)
        if (file != null && file.exists()) {
            values.put(MediaStore.Images.Media.SIZE, file.length())
        }
        val resolver: ContentResolver = context.getContentResolver()
        resolver.update(uri, values, null, null)
    }
    }
}