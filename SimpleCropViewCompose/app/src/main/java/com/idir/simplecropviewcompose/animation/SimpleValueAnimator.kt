package com.idir.simplecropviewcompose.animation

interface SimpleValueAnimator {
    fun startAnimation(duration: Long)
    fun cancelAnimation()
    val isAnimationStarted: Boolean

    fun addAnimatorListener(animatorListener: SimpleValueAnimatorListener?)
}
