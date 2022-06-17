package com.idir.simplecropviewcompose.animation


interface SimpleValueAnimatorListener {
    fun onAnimationStarted()
    fun onAnimationUpdated(scale: Float)
    fun onAnimationFinished()
}
