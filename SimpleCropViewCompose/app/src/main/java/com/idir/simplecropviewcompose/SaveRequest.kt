package com.idir.simplecropviewcompose

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import com.idir.simplecropviewcompose.callback.SaveCallback


class SaveRequest(private val cropImageView: CropImageView, private val image: Bitmap) {
    private var compressFormat: CompressFormat? = null
    private var compressQuality = -1
    fun compressFormat(compressFormat: CompressFormat?): SaveRequest {
        this.compressFormat = compressFormat
        return this
    }

    fun compressQuality(compressQuality: Int): SaveRequest {
        this.compressQuality = compressQuality
        return this
    }

    private fun build() {
        if (compressFormat != null) {
            cropImageView.setCompressFormat(compressFormat)
        }
        if (compressQuality >= 0) {
            cropImageView.setCompressQuality(compressQuality)
        }
    }

    fun execute(saveUri: Uri?, callback: SaveCallback?) {
        build()
        cropImageView.saveAsync(saveUri!!, image, callback)
    }

    fun executeAsSingle(saveUri: Uri?): Single<Uri> {
        build()
        return cropImageView.saveAsSingle(image, saveUri!!)
    }
}
