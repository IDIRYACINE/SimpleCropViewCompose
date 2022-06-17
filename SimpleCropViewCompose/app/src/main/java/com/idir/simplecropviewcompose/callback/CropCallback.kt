package com.idir.simplecropviewcompose.callback

import android.graphics.Bitmap


interface CropCallback : Callback {
    fun onSuccess(cropped: Bitmap?)
}