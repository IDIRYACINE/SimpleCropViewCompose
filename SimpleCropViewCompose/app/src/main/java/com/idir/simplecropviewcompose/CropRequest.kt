package com.idir.simplecropviewcompose


import android.graphics.Bitmap
import android.net.Uri
import com.idir.simplecropviewcompose.callback.CropCallback


class CropRequest(private val cropImageView: CropImageView, private val sourceUri: Uri) {
    private var outputWidth = 0
    private var outputHeight = 0
    private var outputMaxWidth = 0
    private var outputMaxHeight = 0
    fun outputWidth(outputWidth: Int): CropRequest {
        this.outputWidth = outputWidth
        outputHeight = 0
        return this
    }

    fun outputHeight(outputHeight: Int): CropRequest {
        this.outputHeight = outputHeight
        outputWidth = 0
        return this
    }

    fun outputMaxWidth(outputMaxWidth: Int): CropRequest {
        this.outputMaxWidth = outputMaxWidth
        return this
    }

    fun outputMaxHeight(outputMaxHeight: Int): CropRequest {
        this.outputMaxHeight = outputMaxHeight
        return this
    }

    private fun build() {
        if (outputWidth > 0) cropImageView.setOutputWidth(outputWidth)
        if (outputHeight > 0) cropImageView.setOutputHeight(outputHeight)
        cropImageView.setOutputMaxSize(outputMaxWidth, outputMaxHeight)
    }

    fun execute(cropCallback: CropCallback?) {
        build()
        cropImageView.cropAsync(sourceUri, cropCallback)
    }

    fun executeAsSingle(): Single<Bitmap> {
        build()
        return cropImageView.cropAsSingle(sourceUri)
    }
}
