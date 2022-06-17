package com.idir.simplecropviewcompose.util

import android.util.Log

class Logger {

    companion object{
        private val TAG = "SimpleCropView"
        var enabled = false

        fun e(msg: String?) {
            if (!enabled) return
            Log.e(TAG, msg!!)
        }

        fun e(msg: String?, e: Throwable?) {
            if (!enabled) return
            Log.e(TAG, msg, e)
        }

        fun i(msg: String?) {
            if (!enabled) return
            Log.i(TAG, msg!!)
        }

        fun i(msg: String?, e: Throwable?) {
            if (!enabled) return
            Log.i(TAG, msg, e)
        }
    }
}