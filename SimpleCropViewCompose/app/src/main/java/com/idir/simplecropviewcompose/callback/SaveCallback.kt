package com.idir.simplecropviewcompose.callback

import android.net.Uri


interface SaveCallback : Callback {
    fun onSuccess(uri: Uri?)
}