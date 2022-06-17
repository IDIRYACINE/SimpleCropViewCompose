package com.idir.simplecropviewcompose

import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.appcompat.widget.AppCompatImageView
import com.idir.simplecropviewcompose.animation.SimpleValueAnimator
import com.idir.simplecropviewcompose.animation.SimpleValueAnimatorListener
import com.idir.simplecropviewcompose.animation.ValueAnimatorV14
import com.idir.simplecropviewcompose.callback.CropCallback
import com.idir.simplecropviewcompose.callback.LoadCallback
import com.idir.simplecropviewcompose.callback.Callback
import com.idir.simplecropviewcompose.callback.SaveCallback
import com.idir.simplecropviewcompose.util.Logger
import com.idir.simplecropviewcompose.util.Utils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer


class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) :
    AppCompatImageView(context, attrs, defStyle) {
    // Member variables ////////////////////////////////////////////////////////////////////////////
    private var mViewWidth = 0
    private var mViewHeight = 0
    private var mScale = 1.0f
    private var mAngle = 0.0f
    private var mImgWidth = 0.0f
    private var mImgHeight = 0.0f
    private var mIsInitialized = false
    private var mMatrix: Matrix? = null
    private val mPaintTranslucent: Paint
    private val mPaintFrame: Paint
    private val mPaintBitmap: Paint
    private val mPaintDebug: Paint
    private var mFrameRect: RectF? = null
    private var mInitialFrameRect: RectF? = null
    private var mImageRect: RectF? = null
    private var mCenter = PointF()
    private var mLastX = 0f
    private var mLastY = 0f
    private var mIsRotating = false
    private var mIsAnimating = false
    private var mAnimator: SimpleValueAnimator? = null
    private val DEFAULT_INTERPOLATOR: Interpolator = DecelerateInterpolator()
    private var mInterpolator = DEFAULT_INTERPOLATOR
    private val mHandler = Handler(Looper.getMainLooper())

    /**
     * source uri
     *
     * @return source uri
     */
    var sourceUri: Uri? = null
        private set

    /**
     * save uri
     *
     * @return save uri
     */
    var saveUri: Uri? = null
        private set
    private var mExifRotation = 0
    private var mOutputMaxWidth = 0
    private var mOutputMaxHeight = 0
    private var mOutputWidth = 0
    private var mOutputHeight = 0
    private var mIsDebug = false
    private var mCompressFormat: CompressFormat? = CompressFormat.PNG
    private var mCompressQuality = 100
    private var mInputImageWidth = 0
    private var mInputImageHeight = 0
    private var mOutputImageWidth = 0
    private var mOutputImageHeight = 0
    private val mIsLoading = AtomicBoolean(false)
    private val mIsCropping = AtomicBoolean(false)
    private val mIsSaving = AtomicBoolean(false)
    private val mExecutor: ExecutorService

    // Instance variables for customizable attributes //////////////////////////////////////////////
    private var mTouchArea = TouchArea.OUT_OF_BOUNDS
    private var mCropMode: CropMode? = CropMode.SQUARE
    private var mGuideShowMode: ShowMode? = ShowMode.SHOW_ALWAYS
    private var mHandleShowMode: ShowMode? = ShowMode.SHOW_ALWAYS
    private var mMinFrameSize: Float
    private var mHandleSize: Int
    private var mTouchPadding = 0
    private var mShowGuide = true
    private var mShowHandle = true
    private var mIsCropEnabled = true
    private var mIsEnabled = true
    private var mCustomRatio = PointF(1.0f, 1.0f)
    private var mFrameStrokeWeight = 2.0f
    private var mGuideStrokeWeight = 2.0f
    private var mBackgroundColor: Int
    private var mOverlayColor: Int
    private var mFrameColor: Int
    private var mHandleColor: Int
    private var mGuideColor: Int
    private var mInitialFrameScale // 0.01 ~ 1.0, 0.75 is default value
            = 0f
    private var mIsAnimationEnabled = true
    private var mAnimationDurationMillis = DEFAULT_ANIMATION_DURATION_MILLIS
    private var mIsHandleShadowEnabled = true

    // Lifecycle methods ///////////////////////////////////////////////////////////////////////////
    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.mode = mCropMode
        ss.backgroundColor = mBackgroundColor
        ss.overlayColor = mOverlayColor
        ss.frameColor = mFrameColor
        ss.guideShowMode = mGuideShowMode
        ss.handleShowMode = mHandleShowMode
        ss.showGuide = mShowGuide
        ss.showHandle = mShowHandle
        ss.handleSize = mHandleSize
        ss.touchPadding = mTouchPadding
        ss.minFrameSize = mMinFrameSize
        ss.customRatioX = mCustomRatio.x
        ss.customRatioY = mCustomRatio.y
        ss.frameStrokeWeight = mFrameStrokeWeight
        ss.guideStrokeWeight = mGuideStrokeWeight
        ss.isCropEnabled = mIsCropEnabled
        ss.handleColor = mHandleColor
        ss.guideColor = mGuideColor
        ss.initialFrameScale = mInitialFrameScale
        ss.angle = mAngle
        ss.isAnimationEnabled = mIsAnimationEnabled
        ss.animationDuration = mAnimationDurationMillis
        ss.exifRotation = mExifRotation
        ss.sourceUri = sourceUri
        ss.saveUri = saveUri
        ss.compressFormat = mCompressFormat
        ss.compressQuality = mCompressQuality
        ss.isDebug = mIsDebug
        ss.outputMaxWidth = mOutputMaxWidth
        ss.outputMaxHeight = mOutputMaxHeight
        ss.outputWidth = mOutputWidth
        ss.outputHeight = mOutputHeight
        ss.isHandleShadowEnabled = mIsHandleShadowEnabled
        ss.inputImageWidth = mInputImageWidth
        ss.inputImageHeight = mInputImageHeight
        ss.outputImageWidth = mOutputImageWidth
        ss.outputImageHeight = mOutputImageHeight
        return ss
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        mCropMode = ss.mode
        mBackgroundColor = ss.backgroundColor
        mOverlayColor = ss.overlayColor
        mFrameColor = ss.frameColor
        mGuideShowMode = ss.guideShowMode
        mHandleShowMode = ss.handleShowMode
        mShowGuide = ss.showGuide
        mShowHandle = ss.showHandle
        mHandleSize = ss.handleSize
        mTouchPadding = ss.touchPadding
        mMinFrameSize = ss.minFrameSize
        mCustomRatio = PointF(ss.customRatioX, ss.customRatioY)
        mFrameStrokeWeight = ss.frameStrokeWeight
        mGuideStrokeWeight = ss.guideStrokeWeight
        mIsCropEnabled = ss.isCropEnabled
        mHandleColor = ss.handleColor
        mGuideColor = ss.guideColor
        mInitialFrameScale = ss.initialFrameScale
        mAngle = ss.angle
        mIsAnimationEnabled = ss.isAnimationEnabled
        mAnimationDurationMillis = ss.animationDuration
        mExifRotation = ss.exifRotation
        sourceUri = ss.sourceUri
        saveUri = ss.saveUri
        mCompressFormat = ss.compressFormat
        mCompressQuality = ss.compressQuality
        mIsDebug = ss.isDebug
        mOutputMaxWidth = ss.outputMaxWidth
        mOutputMaxHeight = ss.outputMaxHeight
        mOutputWidth = ss.outputWidth
        mOutputHeight = ss.outputHeight
        mIsHandleShadowEnabled = ss.isHandleShadowEnabled
        mInputImageWidth = ss.inputImageWidth
        mInputImageHeight = ss.inputImageHeight
        mOutputImageWidth = ss.outputImageWidth
        mOutputImageHeight = ss.outputImageHeight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(viewWidth, viewHeight)
        mViewWidth = viewWidth - paddingLeft - paddingRight
        mViewHeight = viewHeight - paddingTop - paddingBottom
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (drawable != null) setupLayout(mViewWidth, mViewHeight)
    }

    public override fun onDraw(canvas: Canvas) {
        canvas.drawColor(mBackgroundColor)
        if (mIsInitialized) {
            setMatrix()
            val bm = bitmap
            if (bm != null) {
                canvas.drawBitmap(bm, mMatrix!!, mPaintBitmap)
                // draw edit frame
                drawCropFrame(canvas)
            }
            if (mIsDebug) {
                drawDebugInfo(canvas)
            }
        }
    }

    override fun onDetachedFromWindow() {
        mExecutor.shutdown()
        super.onDetachedFromWindow()
    }

    // Handle styleable ////////////////////////////////////////////////////////////////////////////
    private fun handleStyleable(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
        mDensity: Float
    ) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.scv_CropImageView, defStyle, 0)
        val drawable: Drawable?
        mCropMode = CropMode.SQUARE
        try {
            drawable = ta.getDrawable(R.styleable.scv_CropImageView_scv_img_src)
            drawable?.let { setImageDrawable(it) }
            for (mode in CropMode.values()) {
                if (ta.getInt(R.styleable.scv_CropImageView_scv_crop_mode, 3) == mode.id) {
                    mCropMode = mode
                    break
                }
            }
            mBackgroundColor =
                ta.getColor(R.styleable.scv_CropImageView_scv_background_color, TRANSPARENT)
            mOverlayColor =
                ta.getColor(R.styleable.scv_CropImageView_scv_overlay_color, TRANSLUCENT_BLACK)
            mFrameColor = ta.getColor(R.styleable.scv_CropImageView_scv_frame_color, WHITE)
            mHandleColor = ta.getColor(R.styleable.scv_CropImageView_scv_handle_color, WHITE)
            mGuideColor =
                ta.getColor(R.styleable.scv_CropImageView_scv_guide_color, TRANSLUCENT_WHITE)
            for (mode in ShowMode.values()) {
                if (ta.getInt(R.styleable.scv_CropImageView_scv_guide_show_mode, 1) == mode.id) {
                    mGuideShowMode = mode
                    break
                }
            }
            for (mode in ShowMode.values()) {
                if (ta.getInt(R.styleable.scv_CropImageView_scv_handle_show_mode, 1) == mode.id) {
                    mHandleShowMode = mode
                    break
                }
            }
            setGuideShowMode(mGuideShowMode)
            setHandleShowMode(mHandleShowMode)
            mHandleSize = ta.getDimensionPixelSize(
                R.styleable.scv_CropImageView_scv_handle_size,
                (HANDLE_SIZE_IN_DP * mDensity).toInt()
            )
            mTouchPadding =
                ta.getDimensionPixelSize(R.styleable.scv_CropImageView_scv_touch_padding, 0)
            mMinFrameSize = ta.getDimensionPixelSize(
                R.styleable.scv_CropImageView_scv_min_frame_size,
                (MIN_FRAME_SIZE_IN_DP * mDensity).toInt()
            ).toFloat()
            mFrameStrokeWeight = ta.getDimensionPixelSize(
                R.styleable.scv_CropImageView_scv_frame_stroke_weight,
                (FRAME_STROKE_WEIGHT_IN_DP * mDensity).toInt()
            ).toFloat()
            mGuideStrokeWeight = ta.getDimensionPixelSize(
                R.styleable.scv_CropImageView_scv_guide_stroke_weight,
                (GUIDE_STROKE_WEIGHT_IN_DP * mDensity).toInt()
            ).toFloat()
            mIsCropEnabled = ta.getBoolean(R.styleable.scv_CropImageView_scv_crop_enabled, true)
            mInitialFrameScale = constrain(
                ta.getFloat(
                    R.styleable.scv_CropImageView_scv_initial_frame_scale,
                    DEFAULT_INITIAL_FRAME_SCALE
                ), 0.01f, 1.0f, DEFAULT_INITIAL_FRAME_SCALE
            )
            mIsAnimationEnabled =
                ta.getBoolean(R.styleable.scv_CropImageView_scv_animation_enabled, true)
            mAnimationDurationMillis = ta.getInt(
                R.styleable.scv_CropImageView_scv_animation_duration,
                DEFAULT_ANIMATION_DURATION_MILLIS
            )
            mIsHandleShadowEnabled =
                ta.getBoolean(R.styleable.scv_CropImageView_scv_handle_shadow_enabled, true)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ta.recycle()
        }
    }

    // Drawing method //////////////////////////////////////////////////////////////////////////////
    private fun drawDebugInfo(canvas: Canvas) {
        val fontMetrics = mPaintDebug.fontMetrics
        mPaintDebug.measureText("W")
        val textHeight = (fontMetrics.descent - fontMetrics.ascent).toInt()
        val x = (mImageRect!!.left + mHandleSize.toFloat() * 0.5f * density).toInt()
        var y = (mImageRect!!.top + textHeight + mHandleSize.toFloat() * 0.5f * density).toInt()
        var builder = StringBuilder()
        builder.append("LOADED FROM: ").append(if (sourceUri != null) "Uri" else "Bitmap")
        canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
        builder = StringBuilder()
        if (sourceUri == null) {
            builder.append("INPUT_IMAGE_SIZE: ")
                .append(mImgWidth.toInt())
                .append("x")
                .append(mImgHeight.toInt())
            y += textHeight
            canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
            builder = StringBuilder()
        } else {
            builder = StringBuilder().append("INPUT_IMAGE_SIZE: ")
                .append(mInputImageWidth)
                .append("x")
                .append(mInputImageHeight)
            y += textHeight
            canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
            builder = StringBuilder()
        }
        builder.append("LOADED_IMAGE_SIZE: ")
            .append(bitmap!!.width)
            .append("x")
            .append(bitmap!!.height)
        y += textHeight
        canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
        builder = StringBuilder()
        if (mOutputImageWidth > 0 && mOutputImageHeight > 0) {
            builder.append("OUTPUT_IMAGE_SIZE: ")
                .append(mOutputImageWidth)
                .append("x")
                .append(mOutputImageHeight)
            y += textHeight
            canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
            builder = StringBuilder().append("EXIF ROTATION: ").append(mExifRotation)
            y += textHeight
            canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
            builder = StringBuilder().append("CURRENT_ROTATION: ").append(mAngle.toInt())
            y += textHeight
            canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
        }
        builder = StringBuilder()
        builder.append("FRAME_RECT: ").append(mFrameRect.toString())
        y += textHeight
        canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
        builder = StringBuilder()
        builder.append("ACTUAL_CROP_RECT: ")
            .append(if (actualCropRect != null) actualCropRect.toString() else "")
        y += textHeight
        canvas.drawText(builder.toString(), x.toFloat(), y.toFloat(), mPaintDebug)
    }

    private fun drawCropFrame(canvas: Canvas) {
        if (!mIsCropEnabled) return
        if (mIsRotating) return
        drawOverlay(canvas)
        drawFrame(canvas)
        if (mShowGuide) drawGuidelines(canvas)
        if (mShowHandle) drawHandles(canvas)
    }

    private fun drawOverlay(canvas: Canvas) {
        mPaintTranslucent.isAntiAlias = true
        mPaintTranslucent.isFilterBitmap = true
        mPaintTranslucent.color = mOverlayColor
        mPaintTranslucent.style = Paint.Style.FILL
        val path = Path()
        val overlayRect = RectF(
            Math.floor(mImageRect!!.left.toDouble()).toFloat(), Math.floor(
                mImageRect!!.top.toDouble()
            ).toFloat(), Math.ceil(mImageRect!!.right.toDouble()).toFloat(), Math.ceil(
                mImageRect!!.bottom.toDouble()
            ).toFloat()
        )
        if (!mIsAnimating && (mCropMode == CropMode.CIRCLE || mCropMode == CropMode.CIRCLE_SQUARE)) {
            path.addRect(overlayRect, Path.Direction.CW)
            val circleCenter = PointF(
                (mFrameRect!!.left + mFrameRect!!.right) / 2,
                (mFrameRect!!.top + mFrameRect!!.bottom) / 2
            )
            val circleRadius = (mFrameRect!!.right - mFrameRect!!.left) / 2
            path.addCircle(circleCenter.x, circleCenter.y, circleRadius, Path.Direction.CCW)
            canvas.drawPath(path, mPaintTranslucent)
        } else {
            path.addRect(overlayRect, Path.Direction.CW)
            path.addRect(mFrameRect!!, Path.Direction.CCW)
            canvas.drawPath(path, mPaintTranslucent)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        mPaintFrame.isAntiAlias = true
        mPaintFrame.isFilterBitmap = true
        mPaintFrame.style = Paint.Style.STROKE
        mPaintFrame.color = mFrameColor
        mPaintFrame.strokeWidth = mFrameStrokeWeight
        canvas.drawRect(mFrameRect!!, mPaintFrame)
    }

    private fun drawGuidelines(canvas: Canvas) {
        mPaintFrame.color = mGuideColor
        mPaintFrame.strokeWidth = mGuideStrokeWeight
        val h1 = mFrameRect!!.left + (mFrameRect!!.right - mFrameRect!!.left) / 3.0f
        val h2 = mFrameRect!!.right - (mFrameRect!!.right - mFrameRect!!.left) / 3.0f
        val v1 = mFrameRect!!.top + (mFrameRect!!.bottom - mFrameRect!!.top) / 3.0f
        val v2 = mFrameRect!!.bottom - (mFrameRect!!.bottom - mFrameRect!!.top) / 3.0f
        canvas.drawLine(h1, mFrameRect!!.top, h1, mFrameRect!!.bottom, mPaintFrame)
        canvas.drawLine(h2, mFrameRect!!.top, h2, mFrameRect!!.bottom, mPaintFrame)
        canvas.drawLine(mFrameRect!!.left, v1, mFrameRect!!.right, v1, mPaintFrame)
        canvas.drawLine(mFrameRect!!.left, v2, mFrameRect!!.right, v2, mPaintFrame)
    }

    private fun drawHandles(canvas: Canvas) {
        if (mIsHandleShadowEnabled) drawHandleShadows(canvas)
        mPaintFrame.style = Paint.Style.FILL
        mPaintFrame.color = mHandleColor
        canvas.drawCircle(mFrameRect!!.left, mFrameRect!!.top, mHandleSize.toFloat(), mPaintFrame)
        canvas.drawCircle(mFrameRect!!.right, mFrameRect!!.top, mHandleSize.toFloat(), mPaintFrame)
        canvas.drawCircle(
            mFrameRect!!.left,
            mFrameRect!!.bottom,
            mHandleSize.toFloat(),
            mPaintFrame
        )
        canvas.drawCircle(
            mFrameRect!!.right,
            mFrameRect!!.bottom,
            mHandleSize.toFloat(),
            mPaintFrame
        )
    }

    private fun drawHandleShadows(canvas: Canvas) {
        mPaintFrame.style = Paint.Style.FILL
        mPaintFrame.color = TRANSLUCENT_BLACK
        val rect = RectF(mFrameRect)
        rect.offset(0f, 1f)
        canvas.drawCircle(rect.left, rect.top, mHandleSize.toFloat(), mPaintFrame)
        canvas.drawCircle(rect.right, rect.top, mHandleSize.toFloat(), mPaintFrame)
        canvas.drawCircle(rect.left, rect.bottom, mHandleSize.toFloat(), mPaintFrame)
        canvas.drawCircle(rect.right, rect.bottom, mHandleSize.toFloat(), mPaintFrame)
    }

    private fun setMatrix() {
        mMatrix!!.reset()
        mMatrix!!.setTranslate(mCenter.x - mImgWidth * 0.5f, mCenter.y - mImgHeight * 0.5f)
        mMatrix!!.postScale(mScale, mScale, mCenter.x, mCenter.y)
        mMatrix!!.postRotate(mAngle, mCenter.x, mCenter.y)
    }

    // Layout calculation //////////////////////////////////////////////////////////////////////////
    private fun setupLayout(viewW: Int, viewH: Int) {
        if (viewW == 0 || viewH == 0) return
        setCenter(PointF(paddingLeft + viewW * 0.5f, paddingTop + viewH * 0.5f))
        setScale(calcScale(viewW, viewH, mAngle))
        setMatrix()
        mImageRect = calcImageRect(RectF(0f, 0f, mImgWidth, mImgHeight), mMatrix)
        mFrameRect = if (mInitialFrameRect != null) {
            applyInitialFrameRect(mInitialFrameRect!!)
        } else {
            calcFrameRect(mImageRect)
        }
        mIsInitialized = true
        invalidate()
    }

    private fun calcScale(viewW: Int, viewH: Int, angle: Float): Float {
        mImgWidth = drawable.intrinsicWidth.toFloat()
        mImgHeight = drawable.intrinsicHeight.toFloat()
        if (mImgWidth <= 0) mImgWidth = viewW.toFloat()
        if (mImgHeight <= 0) mImgHeight = viewH.toFloat()
        val viewRatio = viewW.toFloat() / viewH.toFloat()
        val imgRatio = getRotatedWidth(angle) / getRotatedHeight(angle)
        var scale = 1.0f
        if (imgRatio >= viewRatio) {
            scale = viewW / getRotatedWidth(angle)
        } else if (imgRatio < viewRatio) {
            scale = viewH / getRotatedHeight(angle)
        }
        return scale
    }

    private fun calcImageRect(rect: RectF, matrix: Matrix?): RectF {
        val applied = RectF()
        matrix!!.mapRect(applied, rect)
        return applied
    }

    private fun calcFrameRect(imageRect: RectF?): RectF {
        val frameW = getRatioX(imageRect!!.width())
        val frameH = getRatioY(imageRect.height())
        val imgRatio = imageRect.width() / imageRect.height()
        val frameRatio = frameW / frameH
        var l = imageRect.left
        var t = imageRect.top
        var r = imageRect.right
        var b = imageRect.bottom
        if (frameRatio >= imgRatio) {
            l = imageRect.left
            r = imageRect.right
            val hy = (imageRect.top + imageRect.bottom) * 0.5f
            val hh = imageRect.width() / frameRatio * 0.5f
            t = hy - hh
            b = hy + hh
        } else if (frameRatio < imgRatio) {
            t = imageRect.top
            b = imageRect.bottom
            val hx = (imageRect.left + imageRect.right) * 0.5f
            val hw = imageRect.height() * frameRatio * 0.5f
            l = hx - hw
            r = hx + hw
        }
        val w = r - l
        val h = b - t
        val cx = l + w / 2
        val cy = t + h / 2
        val sw = w * mInitialFrameScale
        val sh = h * mInitialFrameScale
        return RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2)
    }

    // Touch Event /////////////////////////////////////////////////////////////////////////////////
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mIsInitialized) return false
        if (!mIsCropEnabled) return false
        if (!mIsEnabled) return false
        if (mIsRotating) return false
        if (mIsAnimating) return false
        if (mIsLoading.get()) return false
        if (mIsCropping.get()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onDown(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onMove(event)
                if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onCancel()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onUp(event)
                return true
            }
        }
        return false
    }

    private fun onDown(e: MotionEvent) {
        invalidate()
        mLastX = e.x
        mLastY = e.y
        checkTouchArea(e.x, e.y)
    }

    private fun onMove(e: MotionEvent) {
        val diffX = e.x - mLastX
        val diffY = e.y - mLastY
        when (mTouchArea) {
            TouchArea.CENTER -> moveFrame(diffX, diffY)
            TouchArea.LEFT_TOP -> moveHandleLT(diffX, diffY)
            TouchArea.RIGHT_TOP -> moveHandleRT(diffX, diffY)
            TouchArea.LEFT_BOTTOM -> moveHandleLB(diffX, diffY)
            TouchArea.RIGHT_BOTTOM -> moveHandleRB(diffX, diffY)
            TouchArea.OUT_OF_BOUNDS -> {}
        }
        invalidate()
        mLastX = e.x
        mLastY = e.y
    }

    private fun onUp(e: MotionEvent) {
        if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = false
        if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = false
        mTouchArea = TouchArea.OUT_OF_BOUNDS
        invalidate()
    }

    private fun onCancel() {
        mTouchArea = TouchArea.OUT_OF_BOUNDS
        invalidate()
    }

    // Hit test ////////////////////////////////////////////////////////////////////////////////////
    private fun checkTouchArea(x: Float, y: Float) {
        if (isInsideCornerLeftTop(x, y)) {
            mTouchArea = TouchArea.LEFT_TOP
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightTop(x, y)) {
            mTouchArea = TouchArea.RIGHT_TOP
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerLeftBottom(x, y)) {
            mTouchArea = TouchArea.LEFT_BOTTOM
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideCornerRightBottom(x, y)) {
            mTouchArea = TouchArea.RIGHT_BOTTOM
            if (mHandleShowMode == ShowMode.SHOW_ON_TOUCH) mShowHandle = true
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            return
        }
        if (isInsideFrame(x, y)) {
            if (mGuideShowMode == ShowMode.SHOW_ON_TOUCH) mShowGuide = true
            mTouchArea = TouchArea.CENTER
            return
        }
        mTouchArea = TouchArea.OUT_OF_BOUNDS
    }

    private fun isInsideFrame(x: Float, y: Float): Boolean {
        if (mFrameRect!!.left <= x && mFrameRect!!.right >= x) {
            if (mFrameRect!!.top <= y && mFrameRect!!.bottom >= y) {
                mTouchArea = TouchArea.CENTER
                return true
            }
        }
        return false
    }

    private fun isInsideCornerLeftTop(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.left
        val dy = y - mFrameRect!!.top
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerRightTop(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.right
        val dy = y - mFrameRect!!.top
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerLeftBottom(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.left
        val dy = y - mFrameRect!!.bottom
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    private fun isInsideCornerRightBottom(x: Float, y: Float): Boolean {
        val dx = x - mFrameRect!!.right
        val dy = y - mFrameRect!!.bottom
        val d = dx * dx + dy * dy
        return sq((mHandleSize + mTouchPadding).toFloat()) >= d
    }

    // Adjust frame ////////////////////////////////////////////////////////////////////////////////
    private fun moveFrame(x: Float, y: Float) {
        mFrameRect!!.left += x
        mFrameRect!!.right += x
        mFrameRect!!.top += y
        mFrameRect!!.bottom += y
        checkMoveBounds()
    }

    private fun moveHandleLT(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.FREE) {
            mFrameRect!!.left += diffX
            mFrameRect!!.top += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.left += dx
            mFrameRect!!.top += dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.top -= offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.left -= offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.left)) {
                ox = mImageRect!!.left - mFrameRect!!.left
                mFrameRect!!.left += ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.top += oy
            }
            if (!isInsideVertical(mFrameRect!!.top)) {
                oy = mImageRect!!.top - mFrameRect!!.top
                mFrameRect!!.top += oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.left += ox
            }
        }
    }

    private fun moveHandleRT(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.FREE) {
            mFrameRect!!.right += diffX
            mFrameRect!!.top += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.right += dx
            mFrameRect!!.top -= dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.top -= offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.top -= offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.right += offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.right)) {
                ox = mFrameRect!!.right - mImageRect!!.right
                mFrameRect!!.right -= ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.top += oy
            }
            if (!isInsideVertical(mFrameRect!!.top)) {
                oy = mImageRect!!.top - mFrameRect!!.top
                mFrameRect!!.top += oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.right -= ox
            }
        }
    }

    private fun moveHandleLB(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.FREE) {
            mFrameRect!!.left += diffX
            mFrameRect!!.bottom += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.left += dx
            mFrameRect!!.bottom -= dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.left -= offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.bottom += offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.left -= offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.left)) {
                ox = mImageRect!!.left - mFrameRect!!.left
                mFrameRect!!.left += ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.bottom -= oy
            }
            if (!isInsideVertical(mFrameRect!!.bottom)) {
                oy = mFrameRect!!.bottom - mImageRect!!.bottom
                mFrameRect!!.bottom -= oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.left += ox
            }
        }
    }

    private fun moveHandleRB(diffX: Float, diffY: Float) {
        if (mCropMode == CropMode.FREE) {
            mFrameRect!!.right += diffX
            mFrameRect!!.bottom += diffY
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
            }
            checkScaleBounds()
        } else {
            val dx = diffX
            val dy = diffX * ratioY / ratioX
            mFrameRect!!.right += dx
            mFrameRect!!.bottom += dy
            if (isWidthTooSmall) {
                val offsetX = mMinFrameSize - frameW
                mFrameRect!!.right += offsetX
                val offsetY = offsetX * ratioY / ratioX
                mFrameRect!!.bottom += offsetY
            }
            if (isHeightTooSmall) {
                val offsetY = mMinFrameSize - frameH
                mFrameRect!!.bottom += offsetY
                val offsetX = offsetY * ratioX / ratioY
                mFrameRect!!.right += offsetX
            }
            var ox: Float
            var oy: Float
            if (!isInsideHorizontal(mFrameRect!!.right)) {
                ox = mFrameRect!!.right - mImageRect!!.right
                mFrameRect!!.right -= ox
                oy = ox * ratioY / ratioX
                mFrameRect!!.bottom -= oy
            }
            if (!isInsideVertical(mFrameRect!!.bottom)) {
                oy = mFrameRect!!.bottom - mImageRect!!.bottom
                mFrameRect!!.bottom -= oy
                ox = oy * ratioX / ratioY
                mFrameRect!!.right -= ox
            }
        }
    }

    // Frame position correction ///////////////////////////////////////////////////////////////////
    private fun checkScaleBounds() {
        val lDiff = mFrameRect!!.left - mImageRect!!.left
        val rDiff = mFrameRect!!.right - mImageRect!!.right
        val tDiff = mFrameRect!!.top - mImageRect!!.top
        val bDiff = mFrameRect!!.bottom - mImageRect!!.bottom
        if (lDiff < 0) {
            mFrameRect!!.left -= lDiff
        }
        if (rDiff > 0) {
            mFrameRect!!.right -= rDiff
        }
        if (tDiff < 0) {
            mFrameRect!!.top -= tDiff
        }
        if (bDiff > 0) {
            mFrameRect!!.bottom -= bDiff
        }
    }

    private fun checkMoveBounds() {
        var diff = mFrameRect!!.left - mImageRect!!.left
        if (diff < 0) {
            mFrameRect!!.left -= diff
            mFrameRect!!.right -= diff
        }
        diff = mFrameRect!!.right - mImageRect!!.right
        if (diff > 0) {
            mFrameRect!!.left -= diff
            mFrameRect!!.right -= diff
        }
        diff = mFrameRect!!.top - mImageRect!!.top
        if (diff < 0) {
            mFrameRect!!.top -= diff
            mFrameRect!!.bottom -= diff
        }
        diff = mFrameRect!!.bottom - mImageRect!!.bottom
        if (diff > 0) {
            mFrameRect!!.top -= diff
            mFrameRect!!.bottom -= diff
        }
    }

    private fun isInsideHorizontal(x: Float): Boolean {
        return mImageRect!!.left <= x && mImageRect!!.right >= x
    }

    private fun isInsideVertical(y: Float): Boolean {
        return mImageRect!!.top <= y && mImageRect!!.bottom >= y
    }

    private val isWidthTooSmall: Boolean
        get() = frameW < mMinFrameSize
    private val isHeightTooSmall: Boolean
        get() = frameH < mMinFrameSize

    // Frame aspect ratio correction ///////////////////////////////////////////////////////////////
    private fun recalculateFrameRect(durationMillis: Int) {
        if (mImageRect == null) return
        if (mIsAnimating) {
            animator!!.cancelAnimation()
        }
        val currentRect = RectF(mFrameRect)
        val newRect = calcFrameRect(mImageRect)
        val diffL = newRect.left - currentRect.left
        val diffT = newRect.top - currentRect.top
        val diffR = newRect.right - currentRect.right
        val diffB = newRect.bottom - currentRect.bottom
        if (mIsAnimationEnabled) {
            val animator = animator
            animator!!.addAnimatorListener(object : SimpleValueAnimatorListener {
                override fun onAnimationStarted() {
                    mIsAnimating = true
                }

                override fun onAnimationUpdated(scale: Float) {
                    mFrameRect = RectF(
                        currentRect.left + diffL * scale, currentRect.top + diffT * scale,
                        currentRect.right + diffR * scale, currentRect.bottom + diffB * scale
                    )
                    invalidate()
                }

                override fun onAnimationFinished() {
                    mFrameRect = newRect
                    invalidate()
                    mIsAnimating = false
                }
            })
            animator.startAnimation(durationMillis.toLong())
        } else {
            mFrameRect = calcFrameRect(mImageRect)
            invalidate()
        }
    }

    private fun getRatioX(w: Float): Float {
        return when (mCropMode) {
            CropMode.FIT_IMAGE -> mImageRect!!.width()
            CropMode.FREE -> w
            CropMode.RATIO_4_3 -> 4F
            CropMode.RATIO_3_4 -> 3F
            CropMode.RATIO_16_9 -> 16F
            CropMode.RATIO_9_16 -> 9F
            CropMode.SQUARE, CropMode.CIRCLE, CropMode.CIRCLE_SQUARE -> 1F
            CropMode.CUSTOM -> mCustomRatio.x
            else -> w
        }
    }

    private fun getRatioY(h: Float): Float {
        return when (mCropMode) {
            CropMode.FIT_IMAGE -> mImageRect!!.height()
            CropMode.FREE -> h
            CropMode.RATIO_4_3 -> 3F
            CropMode.RATIO_3_4 -> 4F
            CropMode.RATIO_16_9 -> 9F
            CropMode.RATIO_9_16 -> 16F
            CropMode.SQUARE, CropMode.CIRCLE, CropMode.CIRCLE_SQUARE -> 1F
            CropMode.CUSTOM -> mCustomRatio.y
            else -> h
        }
    }

    private val ratioX: Float
        get() = when (mCropMode) {
            CropMode.FIT_IMAGE -> mImageRect!!.width()
            CropMode.RATIO_4_3 -> 4F
            CropMode.RATIO_3_4 -> 3F
            CropMode.RATIO_16_9 -> 16F
            CropMode.RATIO_9_16 -> 9F
            CropMode.SQUARE, CropMode.CIRCLE, CropMode.CIRCLE_SQUARE -> 1F
            CropMode.CUSTOM -> mCustomRatio.x
            else -> 1F
        }
    private val ratioY: Float
        get() {
            return when (mCropMode) {
                CropMode.FIT_IMAGE -> mImageRect!!.height()
                CropMode.RATIO_4_3 -> 3F
                CropMode.RATIO_3_4 -> 4F
                CropMode.RATIO_16_9 -> 9F
                CropMode.RATIO_9_16 -> 16F
                CropMode.SQUARE, CropMode.CIRCLE, CropMode.CIRCLE_SQUARE -> 1F
                CropMode.CUSTOM -> mCustomRatio.y
                else -> 1F
            }
        }

    // Utility /////////////////////////////////////////////////////////////////////////////////////
    private val density: Float
        get() {
            val displayMetrics = DisplayMetrics()
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
                .getMetrics(displayMetrics)
            return displayMetrics.density
        }

    private fun sq(value: Float): Float {
        return value * value
    }

    private fun constrain(`val`: Float, min: Float, max: Float, defaultVal: Float): Float {
        return if (`val` < min || `val` > max) defaultVal else `val`
    }

    private fun postErrorOnMainThread(callback: Callback?, e: Throwable) {
        if (callback == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onError(e)
        } else {
            mHandler.post { callback.onError(e) }
        }
    }

    private val bitmap: Bitmap?
        get() {
            var bm: Bitmap? = null
            val d = drawable
            if (d != null && d is BitmapDrawable) bm = d.bitmap
            return bm
        }

    private fun getRotatedWidth(angle: Float): Float {
        return getRotatedWidth(angle, mImgWidth, mImgHeight)
    }

    private fun getRotatedWidth(angle: Float, width: Float, height: Float): Float {
        return if (angle % 180 == 0f) width else height
    }

    private fun getRotatedHeight(angle: Float): Float {
        return getRotatedHeight(angle, mImgWidth, mImgHeight)
    }

    private fun getRotatedHeight(angle: Float, width: Float, height: Float): Float {
        return if (angle % 180 == 0f) height else width
    }

    private fun getRotatedBitmap(bitmap: Bitmap?): Bitmap {
        val rotateMatrix = Matrix()
        rotateMatrix.setRotate(
            mAngle,
            (bitmap!!.width / 2).toFloat(),
            (bitmap.height / 2).toFloat()
        )
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, rotateMatrix,
            true
        )
    }

    // Animation ///////////////////////////////////////////////////////////////////////////////////
    private val animator: SimpleValueAnimator?
        get() {
            setupAnimatorIfNeeded()
            return mAnimator
        }

    private fun setupAnimatorIfNeeded() {
        if (mAnimator == null) {
            mAnimator =
                ValueAnimatorV14(mInterpolator)
        }
    }

    // Cropping ////////////////////////////////////////////////////////////////////////////////////
    @get:Throws(IOException::class)
    private val croppedBitmapFromUri: Bitmap?
        get() {
            var cropped: Bitmap? = null
            var `is`: InputStream? = null
            try {
                `is` = context.contentResolver.openInputStream(sourceUri!!)
                val decoder = BitmapRegionDecoder.newInstance(`is`!!, false)
                val originalImageWidth = decoder!!.width
                val originalImageHeight = decoder.height
                var cropRect: Rect = calcCropRect(originalImageWidth, originalImageHeight)
                if (mAngle != 0f) {
                    val matrix = Matrix()
                    matrix.setRotate(-mAngle)
                    val rotated = RectF()
                    matrix.mapRect(rotated, RectF(cropRect))
                    rotated.offset(
                        if (rotated.left < 0) originalImageWidth else 0F,
                        if (rotated.top < 0) originalImageHeight else 0F
                    )
                    cropRect = Rect(
                        rotated.left.toInt(),
                        rotated.top.toInt(),
                        rotated.right.toInt(),
                        rotated.bottom.toInt()
                    )
                }
                cropped = decoder.decodeRegion(cropRect, BitmapFactory.Options())
                if (mAngle != 0f) {
                    val rotated = getRotatedBitmap(cropped)
                    if (cropped != bitmap && cropped != rotated) {
                        cropped.recycle()
                    }
                    cropped = rotated
                }
            } finally {
                Utils.closeQuietly(`is`)
            }
            return cropped
        }

    private fun calcCropRect(originalImageWidth: Int, originalImageHeight: Int): Rect {
        val scaleToOriginal = getRotatedWidth(
            mAngle,
            originalImageWidth.toFloat(),
            originalImageHeight.toFloat()
        ) / mImageRect!!.width()
        val offsetX = mImageRect!!.left * scaleToOriginal
        val offsetY = mImageRect!!.top * scaleToOriginal
        val left = Math.round(mFrameRect!!.left * scaleToOriginal - offsetX)
        val top = Math.round(mFrameRect!!.top * scaleToOriginal - offsetY)
        val right = Math.round(mFrameRect!!.right * scaleToOriginal - offsetX)
        val bottom = Math.round(mFrameRect!!.bottom * scaleToOriginal - offsetY)
        val imageW = Math.round(
            getRotatedWidth(
                mAngle,
                originalImageWidth.toFloat(),
                originalImageHeight.toFloat()
            )
        )
        val imageH = Math.round(
            getRotatedHeight(
                mAngle,
                originalImageWidth.toFloat(),
                originalImageHeight.toFloat()
            )
        )
        return Rect(
            Math.max(left, 0), Math.max(top, 0), Math.min(right, imageW),
            Math.min(bottom, imageH)
        )
    }

    private fun scaleBitmapIfNeeded(cropp: Bitmap): Bitmap {
        var cropped :Bitmap = cropp
        val width = cropped.width
        val height = cropped.height
        var outWidth = 0
        var outHeight = 0
        val imageRatio = getRatioX(mFrameRect!!.width()) / getRatioY(mFrameRect!!.height())
        if (mOutputWidth > 0) {
            outWidth = mOutputWidth
            outHeight = Math.round(mOutputWidth / imageRatio)
        } else if (mOutputHeight > 0) {
            outHeight = mOutputHeight
            outWidth = Math.round(mOutputHeight * imageRatio)
        } else {
            if (mOutputMaxWidth > 0 && mOutputMaxHeight > 0 && (width > mOutputMaxWidth
                        || height > mOutputMaxHeight)
            ) {
                val maxRatio = mOutputMaxWidth.toFloat() / mOutputMaxHeight.toFloat()
                if (maxRatio >= imageRatio) {
                    outHeight = mOutputMaxHeight
                    outWidth = Math.round(mOutputMaxHeight.toFloat() * imageRatio)
                } else {
                    outWidth = mOutputMaxWidth
                    outHeight = Math.round(mOutputMaxWidth.toFloat() / imageRatio)
                }
            }
        }
        if (outWidth > 0 && outHeight > 0) {
            val scaled: Bitmap = Utils.getScaledBitmap(cropped, outWidth, outHeight)
            if (cropped != bitmap && cropped != scaled) {
                cropped.recycle()
            }
            cropped = scaled
        }
        return cropped
    }

    // File save ///////////////////////////////////////////////////////////////////////////////////
    @Throws(IOException::class, IllegalStateException::class)
    private fun saveImage(bitmap: Bitmap?, uri: Uri): Uri {
        saveUri = uri
        checkNotNull(saveUri) { "Save uri must not be null." }
        var outputStream: OutputStream? = null
        return try {
            outputStream = context.contentResolver.openOutputStream(uri)
            bitmap!!.compress(mCompressFormat, mCompressQuality, outputStream)
            Utils.copyExifInfo(context, sourceUri, uri, bitmap.width, bitmap.height)
            Utils.updateGalleryInfo(context, uri)
            uri
        } finally {
            Utils.closeQuietly(outputStream)
        }
    }
    // Public methods //////////////////////////////////////////////////////////////////////////////
    /**
     * Get source image bitmap
     *
     * @return src bitmap
     */
    val imageBitmap: Bitmap?
        get() = bitmap

    /**
     * Set source image bitmap
     *
     * @param bitmap src image bitmap
     */
    override fun setImageBitmap(bitmap: Bitmap) {
        super.setImageBitmap(bitmap) // calls setImageDrawable internally
    }

    /**
     * Set source image resource id
     *
     * @param resId source image resource id
     */
    override fun setImageResource(resId: Int) {
        mIsInitialized = false
        resetImageInfo()
        super.setImageResource(resId)
        updateLayout()
    }

    /**
     * Set image drawable.
     *
     * @param drawable source image drawable
     */
    override fun setImageDrawable(drawable: Drawable?) {
        mIsInitialized = false
        resetImageInfo()
        setImageDrawableInternal(drawable)
    }

    private fun setImageDrawableInternal(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateLayout()
    }

    /**
     * Set image uri
     *
     * @param uri source image local uri
     */
    override fun setImageURI(uri: Uri?) {
        mIsInitialized = false
        super.setImageURI(uri)
        updateLayout()
    }

    private fun updateLayout() {
        val d = drawable
        if (d != null) {
            setupLayout(mViewWidth, mViewHeight)
        }
    }

    private fun resetImageInfo() {
        if (mIsLoading.get()) return
        sourceUri = null
        saveUri = null
        mInputImageWidth = 0
        mInputImageHeight = 0
        mOutputImageWidth = 0
        mOutputImageHeight = 0
        mAngle = mExifRotation.toFloat()
    }

    /**
     * Load image from Uri.
     * This method is deprecated. Use loadAsync(Uri, LoadCallback) instead.
     *
     * @param sourceUri Image Uri
     * @param callback Callback
     *
     * @see .loadAsync
     */
    fun startLoad(sourceUri: Uri, callback: LoadCallback?) {
        loadAsync(sourceUri, callback)
    }

    /**
     * Load image from Uri.
     *
     * @param sourceUri Image Uri
     * @param callback Callback
     *
     * @see .loadAsync
     */
    fun loadAsync(sourceUri: Uri, callback: LoadCallback?) {
        loadAsync(sourceUri, false, null, callback)
    }

    /**
     * Load image from Uri.
     *
     * @param sourceUri Image Uri
     * @param callback Callback
     *
     * @see .load
     * @see .loadAsCompletable
     */
    fun loadAsync(
        sourceUri: Uri, useThumbnail: Boolean,
        initialFrameRect: RectF?, callback: LoadCallback?
    ) {
        mExecutor.submit(object : Runnable {
            override fun run() {
                try {
                    mIsLoading.set(true)
                    //this.sourceUri = sourceUri
                    mInitialFrameRect = initialFrameRect
                    if (useThumbnail) {
                        applyThumbnail(sourceUri)
                    }
                    val sampled = getImage(sourceUri)
                    mHandler.post {
                        mAngle = mExifRotation.toFloat()
                        setImageDrawableInternal(BitmapDrawable(resources, sampled))
                        callback?.onSuccess()
                    }
                } catch (e: Exception) {
                    postErrorOnMainThread(callback, e)
                } finally {
                    mIsLoading.set(false)
                }
            }
        })
    }

    /**
     * Load image from Uri with RxJava2
     *
     * @param sourceUri Image Uri
     *
     * @see .load
     */
    fun loadAsCompletable(sourceUri: Uri?): Completable {
        return loadAsCompletable(sourceUri, false, null)
    }

    /**
     * Load image from Uri with RxJava2
     *
     * @param sourceUri Image Uri
     *
     * @see .load
     * @return Completable of loading image
     */
    fun loadAsCompletable(
        sourceUri: Uri?, useThumbnail: Boolean,
        initialFrameRect: RectF?
    ): Completable {
        return Completable.create(object : CompletableOnSubscribe() {
            @Throws(Exception::class)
            fun subscribe(emitter: CompletableEmitter) {
                mInitialFrameRect = initialFrameRect
                this.sourceUri = sourceUri
                if (useThumbnail) {
                    applyThumbnail(sourceUri)
                }
                val sampled = getImage(sourceUri)
                mHandler.post {
                    mAngle = mExifRotation.toFloat()
                    setImageDrawableInternal(BitmapDrawable(resources, sampled))
                    emitter.onComplete()
                }
            }
        }).doOnSubscribe(object : Consumer<Disposable?>() {
            @Throws(Exception::class)
            fun accept(disposable: Disposable) {
                mIsLoading.set(true)
            }
        }).doFinally(object : Action() {
            @Throws(Exception::class)
            fun run() {
                mIsLoading.set(false)
            }
        })
    }

    /**
     * Load image from Uri with Builder Pattern
     *
     * @param sourceUri Image Uri
     *
     * @return Builder
     */
    fun load(sourceUri: Uri): LoadRequest {
        return LoadRequest(this, sourceUri)
    }

    private fun applyThumbnail(sourceUri: Uri) {
        val thumb = getThumbnail(sourceUri) ?: return
        mHandler.post {
            mAngle = mExifRotation.toFloat()
            setImageDrawableInternal(BitmapDrawable(resources, thumb))
        }
    }

    private fun getImage(sourceUri: Uri): Bitmap? {
        checkNotNull(sourceUri) { "Source Uri must not be null." }
        mExifRotation = Utils.getExifOrientation(context, this.sourceUri!!)
        val maxSize: Int = Utils.getMaxSize()
        var requestSize = Math.max(mViewWidth, mViewHeight)
        if (requestSize == 0) requestSize = maxSize
        val sampledBitmap: Bitmap? = Utils.decodeSampledBitmapFromUri(
            context,
            this.sourceUri!!, requestSize
        )
        mInputImageWidth = Utils.sInputImageWidth
        mInputImageHeight = Utils.sInputImageHeight
        return sampledBitmap
    }

    private fun getThumbnail(sourceUri: Uri): Bitmap? {
        checkNotNull(sourceUri) { "Source Uri must not be null." }
        mExifRotation = Utils.getExifOrientation(context, this.sourceUri!!)
        val requestSize = (Math.max(mViewWidth, mViewHeight) * 0.1f).toInt()
        if (requestSize == 0) return null
        val sampledBitmap: Bitmap? = Utils.decodeSampledBitmapFromUri(
            context,
            this.sourceUri!!, requestSize
        )
        mInputImageWidth = Utils.sInputImageWidth
        mInputImageHeight = Utils.sInputImageHeight
        return sampledBitmap
    }
    /**
     * Rotate image
     *
     * @param degrees rotation angle
     * @param durationMillis animation duration in milliseconds
     */
    /**
     * Rotate image
     *
     * @param degrees rotation angle
     */
    @JvmOverloads
    fun rotateImage(degrees: RotateDegrees, durationMillis: Int = mAnimationDurationMillis) {
        if (mIsRotating) {
            animator!!.cancelAnimation()
        }
        val currentAngle = mAngle
        val newAngle = mAngle + degrees.value
        val angleDiff = newAngle - currentAngle
        val currentScale = mScale
        val newScale = calcScale(mViewWidth, mViewHeight, newAngle)
        if (mIsAnimationEnabled) {
            val scaleDiff = newScale - currentScale
            val animator = animator
            animator!!.addAnimatorListener(object : SimpleValueAnimatorListener {
                override fun onAnimationStarted() {
                    mIsRotating = true
                }

                override fun onAnimationUpdated(scale: Float) {
                    mAngle = currentAngle + angleDiff * scale
                    mScale = currentScale + scaleDiff * scale
                    setMatrix()
                    invalidate()
                }

                override fun onAnimationFinished() {
                    mAngle = newAngle % 360
                    mScale = newScale
                    mInitialFrameRect = null
                    setupLayout(mViewWidth, mViewHeight)
                    mIsRotating = false
                }
            })
            animator.startAnimation(durationMillis.toLong())
        } else {
            mAngle = newAngle % 360
            mScale = newScale
            setupLayout(mViewWidth, mViewHeight)
        }
    }

    /**
     * Get cropped image bitmap
     *
     * @return cropped image bitmap
     */
    private val croppedBitmap: Bitmap?
        get() {
            val source = bitmap ?: return null
            val rotated = getRotatedBitmap(source)
            val cropRect = calcCropRect(source.width, source.height)
            var cropped = Bitmap.createBitmap(
                rotated, cropRect.left, cropRect.top, cropRect.width(),
                cropRect.height(), null, false
            )
            if (rotated != cropped && rotated != source) {
                rotated.recycle()
            }
            if (mCropMode == CropMode.CIRCLE) {
                val circle = getCircularBitmap(cropped)
                if (cropped != bitmap) {
                    cropped!!.recycle()
                }
                cropped = circle
            }
            return cropped
        }

    /**
     * Crop the square image in a circular
     *
     * @param square image bitmap
     * @return circular image bitmap
     */
    fun getCircularBitmap(square: Bitmap?): Bitmap? {
        if (square == null) return null
        val output = Bitmap.createBitmap(square.width, square.height, Bitmap.Config.ARGB_8888)
        val rect = Rect(0, 0, square.width, square.height)
        val canvas = Canvas(output)
        val halfWidth = square.width / 2
        val halfHeight = square.height / 2
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawCircle(
            halfWidth.toFloat(),
            halfHeight.toFloat(),
            Math.min(halfWidth, halfHeight).toFloat(),
            paint
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(square, rect, rect, paint)
        return output
    }

    /**
     * Crop image
     * This method is separated to #crop(Uri) and #save(Bitmap)
     * Use #crop(Uri) and #save(Bitmap)
     *
     * @param saveUri Uri for saving the cropped image
     * @param cropCallback Callback for cropping the image
     * @param saveCallback Callback for saving the image
     *
     * @see .crop
     * @see .save
     */
    fun startCrop(
        saveUri: Uri, cropCallback: CropCallback?,
        saveCallback: SaveCallback?
    ) {
        mExecutor.submit {
            var croppedImage: Bitmap? = null
            try {
                mIsCropping.set(true)
                croppedImage = cropImage()
                val cropped = croppedImage
                mHandler.post {
                    cropCallback?.onSuccess(cropped)
                    if (mIsDebug) invalidate()
                }
                saveImage(croppedImage, saveUri)
                mHandler.post { if (saveCallback != null) saveCallback.onSuccess(saveUri) }
            } catch (e: Exception) {
                if (croppedImage == null) {
                    postErrorOnMainThread(cropCallback, e)
                } else {
                    postErrorOnMainThread(saveCallback, e)
                }
            } finally {
                mIsCropping.set(false)
            }
        }
    }

    /**
     * Crop image
     *
     * @param sourceUri Uri for cropping(If null, the Uri set in loadAsync() is used)
     * @param cropCallback Callback for cropping the image
     *
     * @see .crop
     * @see .cropAsSingle
     */
    fun cropAsync(sourceUri: Uri?, cropCallback: CropCallback?) {
        mExecutor.submit(object : Runnable {
            override fun run() {
                try {
                    mIsCropping.set(true)
                    if (sourceUri != null) this.sourceUri = sourceUri
                    val cropped = cropImage()
                    mHandler.post {
                        cropCallback?.onSuccess(cropped)
                        if (mIsDebug) invalidate()
                    }
                } catch (e: Exception) {
                    postErrorOnMainThread(cropCallback, e)
                } finally {
                    mIsCropping.set(false)
                }
            }
        })
    }

    fun cropAsync(cropCallback: CropCallback?) {
        cropAsync(null, cropCallback)
    }

    /**
     * Crop image with RxJava2
     *
     * @param sourceUri Uri for cropping(If null, the Uri set in loadAsSingle() is used)
     *
     * @return Single of cropping image
     */
    @JvmOverloads
    fun cropAsSingle(sourceUri: Uri? = null): Single<Bitmap> {
        return Single.fromCallable(object : Callable<Bitmap?> {
            @Throws(Exception::class)
            override fun call(): Bitmap {
                if (sourceUri != null) this.sourceUri = sourceUri
                return cropImage()
            }
        }).doOnSubscribe(object : Consumer<Disposable?>() {
            @Throws(Exception::class)
            fun accept(disposable: Disposable) {
                mIsCropping.set(true)
            }
        }).doFinally(object : Action() {
            @Throws(Exception::class)
            fun run() {
                mIsCropping.set(false)
            }
        })
    }

    /**
     * Crop image with Builder Pattern
     *
     * @param sourceUri Uri for cropping(If null, the Uri set in loadAsSingle() is used)
     *
     * @return Builder
     */
    fun crop(sourceUri: Uri): CropRequest {
        return CropRequest(this, sourceUri)
    }

    /**
     * Save image
     *
     * @param saveUri Uri for saving the cropped image
     * @param image Image for saving
     * @param saveCallback Callback for saving the image
     */
    fun saveAsync(saveUri: Uri, image: Bitmap?, saveCallback: SaveCallback?) {
        mExecutor.submit {
            try {
                mIsSaving.set(true)
                saveImage(image, saveUri)
                mHandler.post { if (saveCallback != null) saveCallback.onSuccess(saveUri) }
            } catch (e: Exception) {
                postErrorOnMainThread(saveCallback, e)
            } finally {
                mIsSaving.set(false)
            }
        }
    }

    /**
     * Save image with RxJava2
     *
     * @param bitmap Bitmap for saving
     * @param saveUri Uri for saving the cropped image
     *
     * @return Single of saving image
     */
    fun saveAsSingle(bitmap: Bitmap?, saveUri: Uri): Single<Uri> {
        return Single.fromCallable(Callable { saveImage(bitmap, saveUri) })
            .doOnSubscribe(object : Consumer<Disposable?>() {
                @Throws(Exception::class)
                fun accept(disposable: Disposable) {
                    mIsSaving.set(true)
                }
            }).doFinally(object : Action() {
                @Throws(Exception::class)
                fun run() {
                    mIsSaving.set(false)
                }
            })
    }

    /**
     * Save image with Builder Pattern
     *
     * @param bitmap image for saving
     *
     * @return Builder
     */
    fun save(bitmap: Bitmap): SaveRequest {
        return SaveRequest(this, bitmap)
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun cropImage(): Bitmap {
        var cropped: Bitmap

        // Use thumbnail for getCroppedBitmap
        if (sourceUri == null) {
            cropped = croppedBitmap!!
        } else {
            cropped = croppedBitmapFromUri!!
            if (mCropMode == CropMode.CIRCLE) {
                val circle = getCircularBitmap(cropped)
                if (cropped != bitmap) {
                    cropped.recycle()
                }
                cropped = circle!!
            }
        }
        cropped = scaleBitmapIfNeeded(cropped)
        mOutputImageWidth = cropped.width
        mOutputImageHeight = cropped.height
        return cropped
    }

    /**
     * Get frame position relative to the source bitmap.
     * @see .load
     * @see .loadAsync
     * @see .loadAsCompletable
     * @return getCroppedBitmap area boundaries.
     */
    val actualCropRect: RectF?
        get() {
            if (mImageRect == null) return null
            val offsetX = mImageRect!!.left / mScale
            val offsetY = mImageRect!!.top / mScale
            var l = mFrameRect!!.left / mScale - offsetX
            var t = mFrameRect!!.top / mScale - offsetY
            var r = mFrameRect!!.right / mScale - offsetX
            var b = mFrameRect!!.bottom / mScale - offsetY
            l = Math.max(0f, l)
            t = Math.max(0f, t)
            r = Math.min(mImageRect!!.right / mScale, r)
            b = Math.min(mImageRect!!.bottom / mScale, b)
            return RectF(l, t, r, b)
        }

    private fun applyInitialFrameRect(initialFrameRect: RectF): RectF {
        val frameRect = RectF()
        frameRect[initialFrameRect.left * mScale, initialFrameRect.top * mScale, initialFrameRect.right * mScale] =
            initialFrameRect.bottom * mScale
        frameRect.offset(mImageRect!!.left, mImageRect!!.top)
        val l = Math.max(mImageRect!!.left, frameRect.left)
        val t = Math.max(mImageRect!!.top, frameRect.top)
        val r = Math.min(mImageRect!!.right, frameRect.right)
        val b = Math.min(mImageRect!!.bottom, frameRect.bottom)
        frameRect[l, t, r] = b
        return frameRect
    }

    /**
     * Set getCroppedBitmap mode
     *
     * @param mode getCroppedBitmap mode
     * @param durationMillis animation duration in milliseconds
     */
    fun setCropMode(mode: CropMode, durationMillis: Int) {
        if (mode == CropMode.CUSTOM) {
            setCustomRatio(1, 1)
        } else {
            mCropMode = mode
            recalculateFrameRect(durationMillis)
        }
    }

    /**
     * Set getCroppedBitmap mode
     *
     * @param mode getCroppedBitmap mode
     */
    fun setCropMode(mode: CropMode) {
        setCropMode(mode, mAnimationDurationMillis)
    }

    /**
     * Set custom aspect ratio to getCroppedBitmap frame
     *
     * @param ratioX ratio x
     * @param ratioY ratio y
     * @param durationMillis animation duration in milliseconds
     */
    fun setCustomRatio(ratioX: Int, ratioY: Int, durationMillis: Int) {
        if (ratioX == 0 || ratioY == 0) return
        mCropMode = CropMode.CUSTOM
        mCustomRatio = PointF(ratioX.toFloat(), ratioY.toFloat())
        recalculateFrameRect(durationMillis)
    }

    /**
     * Set custom aspect ratio to getCroppedBitmap frame
     *
     * @param ratioX ratio x
     * @param ratioY ratio y
     */
    fun setCustomRatio(ratioX: Int, ratioY: Int) {
        setCustomRatio(ratioX, ratioY, mAnimationDurationMillis)
    }

    /**
     * Set image overlay color
     *
     * @param overlayColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setOverlayColor(overlayColor: Int) {
        mOverlayColor = overlayColor
        invalidate()
    }

    /**
     * Set getCroppedBitmap frame color
     *
     * @param frameColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setFrameColor(frameColor: Int) {
        mFrameColor = frameColor
        invalidate()
    }

    /**
     * Set handle color
     *
     * @param handleColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setHandleColor(handleColor: Int) {
        mHandleColor = handleColor
        invalidate()
    }

    /**
     * Set guide color
     *
     * @param guideColor color resId or color int(ex. 0xFFFFFFFF)
     */
    fun setGuideColor(guideColor: Int) {
        mGuideColor = guideColor
        invalidate()
    }

    /**
     * Set view background color
     *
     * @param bgColor color resId or color int(ex. 0xFFFFFFFF)
     */
    override fun setBackgroundColor(bgColor: Int) {
        mBackgroundColor = bgColor
        invalidate()
    }

    /**
     * Set getCroppedBitmap frame minimum size in density-independent pixels.
     *
     * @param minDp getCroppedBitmap frame minimum size in density-independent pixels
     */
    fun setMinFrameSizeInDp(minDp: Int) {
        mMinFrameSize = minDp * density
    }

    /**
     * Set getCroppedBitmap frame minimum size in pixels.
     *
     * @param minPx getCroppedBitmap frame minimum size in pixels
     */
    fun setMinFrameSizeInPx(minPx: Int) {
        mMinFrameSize = minPx.toFloat()
    }

    /**
     * Set handle radius in density-independent pixels.
     *
     * @param handleDp handle radius in density-independent pixels
     */
    fun setHandleSizeInDp(handleDp: Int) {
        mHandleSize = (handleDp * density).toInt()
    }

    /**
     * Set getCroppedBitmap frame handle touch padding(touch area) in density-independent pixels.
     *
     * handle touch area : a circle of radius R.(R = handle size + touch padding)
     *
     * @param paddingDp getCroppedBitmap frame handle touch padding(touch area) in
     * density-independent
     * pixels
     */
    fun setTouchPaddingInDp(paddingDp: Int) {
        mTouchPadding = (paddingDp * density).toInt()
    }

    /**
     * Set guideline show mode.
     * (SHOW_ALWAYS/NOT_SHOW/SHOW_ON_TOUCH)
     *
     * @param mode guideline show mode
     */
    fun setGuideShowMode(mode: ShowMode?) {
        mGuideShowMode = mode
        when (mode) {
            ShowMode.SHOW_ALWAYS -> mShowGuide = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowGuide = false
        }
        invalidate()
    }

    /**
     * Set handle show mode.
     * (SHOW_ALWAYS/NOT_SHOW/SHOW_ON_TOUCH)
     *
     * @param mode handle show mode
     */
    fun setHandleShowMode(mode: ShowMode?) {
        mHandleShowMode = mode
        when (mode) {
            ShowMode.SHOW_ALWAYS -> mShowHandle = true
            ShowMode.NOT_SHOW, ShowMode.SHOW_ON_TOUCH -> mShowHandle = false
        }
        invalidate()
    }

    /**
     * Set frame stroke weight in density-independent pixels.
     *
     * @param weightDp frame stroke weight in density-independent pixels.
     */
    fun setFrameStrokeWeightInDp(weightDp: Int) {
        mFrameStrokeWeight = weightDp * density
        invalidate()
    }

    /**
     * Set guideline stroke weight in density-independent pixels.
     *
     * @param weightDp guideline stroke weight in density-independent pixels.
     */
    fun setGuideStrokeWeightInDp(weightDp: Int) {
        mGuideStrokeWeight = weightDp * density
        invalidate()
    }

    /**
     * Set whether to show getCroppedBitmap frame.
     *
     * @param enabled should show getCroppedBitmap frame?
     */
    fun setCropEnabled(enabled: Boolean) {
        mIsCropEnabled = enabled
        invalidate()
    }

    /**
     * Set locking the getCroppedBitmap frame.
     *
     * @param enabled should lock getCroppedBitmap frame?
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        mIsEnabled = enabled
    }

    /**
     * Set initial scale of the frame.(0.01 ~ 1.0)
     *
     * @param initialScale initial scale
     */
    fun setInitialFrameScale(initialScale: Float) {
        mInitialFrameScale = constrain(initialScale, 0.01f, 1.0f, DEFAULT_INITIAL_FRAME_SCALE)
    }

    /**
     * Set whether to animate
     *
     * @param enabled is animation enabled?
     */
    fun setAnimationEnabled(enabled: Boolean) {
        mIsAnimationEnabled = enabled
    }

    /**
     * Set duration of animation
     *
     * @param durationMillis animation duration in milliseconds
     */
    fun setAnimationDuration(durationMillis: Int) {
        mAnimationDurationMillis = durationMillis
    }

    /**
     * Set interpolator of animation
     * (Default interpolator is DecelerateInterpolator)
     *
     * @param interpolator interpolator used for animation
     */
    fun setInterpolator(interpolator: Interpolator) {
        mInterpolator = interpolator
        mAnimator = null
        setupAnimatorIfNeeded()
    }

    /**
     * Set whether to show debug display
     *
     * @param debug is logging enabled
     */
    fun setDebug(debug: Boolean) {
        mIsDebug = debug
        Logger.enabled = true
        invalidate()
    }

    /**
     * Set whether to log exception
     *
     * @param enabled is logging enabled
     */
    fun setLoggingEnabled(enabled: Boolean) {
        Logger.enabled = enabled
    }

    /**
     * Set fixed width for output
     * (After cropping, the image is scaled to the specified size.)
     *
     * @param outputWidth output width
     */
    fun setOutputWidth(outputWidth: Int) {
        mOutputWidth = outputWidth
        mOutputHeight = 0
    }

    /**
     * Set fixed height for output
     * (After cropping, the image is scaled to the specified size.)
     *
     * @param outputHeight output height
     */
    fun setOutputHeight(outputHeight: Int) {
        mOutputHeight = outputHeight
        mOutputWidth = 0
    }

    /**
     * Set maximum size for output
     * (If cropped image size is larger than max size, the image is scaled to the smaller size.
     * If fixed output width/height has already set, these parameters are ignored.)
     *
     * @param maxWidth max output width
     * @param maxHeight max output height
     */
    fun setOutputMaxSize(maxWidth: Int, maxHeight: Int) {
        mOutputMaxWidth = maxWidth
        mOutputMaxHeight = maxHeight
    }

    /**
     * Set compress format for output
     *
     * @param format compress format[android.graphics.Bitmap.CompressFormat]
     */
    fun setCompressFormat(format: CompressFormat?) {
        mCompressFormat = format
    }

    /**
     * Set compress quality for output
     *
     * @param quality compress quality(0-100: 100 is default.)
     */
    fun setCompressQuality(quality: Int) {
        mCompressQuality = quality
    }

    /**
     * Set whether to show handle shadows
     *
     * @param handleShadowEnabled should show handle shadows?
     */
    fun setHandleShadowEnabled(handleShadowEnabled: Boolean) {
        mIsHandleShadowEnabled = handleShadowEnabled
    }

    /**
     * cropping status
     *
     * @return is cropping process running
     */
    val isCropping: Boolean
        get() = mIsCropping.get()

    /**
     * saving status
     *
     * @return is saving process running
     */
    val isSaving: Boolean
        get() = mIsSaving.get()

    private fun setScale(mScale: Float) {
        this.mScale = mScale
    }

    private fun setCenter(mCenter: PointF) {
        this.mCenter = mCenter
    }

    private val frameW: Float
        get() = (mFrameRect!!.right - mFrameRect!!.left)
    private val frameH: Float
        get() = (mFrameRect!!.bottom - mFrameRect!!.top)

    // Enum ////////////////////////////////////////////////////////////////////////////////////////
    private enum class TouchArea {
        OUT_OF_BOUNDS, CENTER, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM
    }

    enum class CropMode(val id: Int) {
        FIT_IMAGE(0), RATIO_4_3(1), RATIO_3_4(2), SQUARE(3), RATIO_16_9(4), RATIO_9_16(5), FREE(
            6
        ),
        CUSTOM(7), CIRCLE(8), CIRCLE_SQUARE(9);

    }

    enum class ShowMode(val id: Int) {
        SHOW_ALWAYS(1), SHOW_ON_TOUCH(2), NOT_SHOW(3);

    }

    enum class RotateDegrees(val value: Int) {
        ROTATE_90D(90), ROTATE_180D(180), ROTATE_270D(270), ROTATE_M90D(-90), ROTATE_M180D(
            -180
        ),
        ROTATE_M270D(-270);

    }

    // Save/Restore support ////////////////////////////////////////////////////////////////////////
    class SavedState : BaseSavedState {
        var mode: CropMode? = null
        var backgroundColor = 0
        var overlayColor = 0
        var frameColor = 0
        var guideShowMode: ShowMode? = null
        var handleShowMode: ShowMode? = null
        var showGuide = false
        var showHandle = false
        var handleSize = 0
        var touchPadding = 0
        var minFrameSize = 0f
        var customRatioX = 0f
        var customRatioY = 0f
        var frameStrokeWeight = 0f
        var guideStrokeWeight = 0f
        var isCropEnabled = false
        var handleColor = 0
        var guideColor = 0
        var initialFrameScale = 0f
        var angle = 0f
        var isAnimationEnabled = false
        var animationDuration = 0
        var exifRotation = 0
        var sourceUri: Uri? = null
        var saveUri: Uri? = null
        var compressFormat: CompressFormat? = null
        var compressQuality = 0
        var isDebug = false
        var outputMaxWidth = 0
        var outputMaxHeight = 0
        var outputWidth = 0
        var outputHeight = 0
        var isHandleShadowEnabled = false
        var inputImageWidth = 0
        var inputImageHeight = 0
        var outputImageWidth = 0
        var outputImageHeight = 0

        internal constructor(superState: Parcelable?) : super(superState) {}
        private constructor(`in`: Parcel) : super(`in`) {
            mode = `in`.readSerializable() as CropMode?
            backgroundColor = `in`.readInt()
            overlayColor = `in`.readInt()
            frameColor = `in`.readInt()
            guideShowMode = `in`.readSerializable() as ShowMode?
            handleShowMode = `in`.readSerializable() as ShowMode?
            showGuide = `in`.readInt() != 0
            showHandle = `in`.readInt() != 0
            handleSize = `in`.readInt()
            touchPadding = `in`.readInt()
            minFrameSize = `in`.readFloat()
            customRatioX = `in`.readFloat()
            customRatioY = `in`.readFloat()
            frameStrokeWeight = `in`.readFloat()
            guideStrokeWeight = `in`.readFloat()
            isCropEnabled = `in`.readInt() != 0
            handleColor = `in`.readInt()
            guideColor = `in`.readInt()
            initialFrameScale = `in`.readFloat()
            angle = `in`.readFloat()
            isAnimationEnabled = `in`.readInt() != 0
            animationDuration = `in`.readInt()
            exifRotation = `in`.readInt()
            sourceUri = `in`.readParcelable(Uri::class.java.classLoader)
            saveUri = `in`.readParcelable(Uri::class.java.classLoader)
            compressFormat = `in`.readSerializable() as CompressFormat?
            compressQuality = `in`.readInt()
            isDebug = `in`.readInt() != 0
            outputMaxWidth = `in`.readInt()
            outputMaxHeight = `in`.readInt()
            outputWidth = `in`.readInt()
            outputHeight = `in`.readInt()
            isHandleShadowEnabled = `in`.readInt() != 0
            inputImageWidth = `in`.readInt()
            inputImageHeight = `in`.readInt()
            outputImageWidth = `in`.readInt()
            outputImageHeight = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flag: Int) {
            super.writeToParcel(out, flag)
            out.writeSerializable(mode)
            out.writeInt(backgroundColor)
            out.writeInt(overlayColor)
            out.writeInt(frameColor)
            out.writeSerializable(guideShowMode)
            out.writeSerializable(handleShowMode)
            out.writeInt(if (showGuide) 1 else 0)
            out.writeInt(if (showHandle) 1 else 0)
            out.writeInt(handleSize)
            out.writeInt(touchPadding)
            out.writeFloat(minFrameSize)
            out.writeFloat(customRatioX)
            out.writeFloat(customRatioY)
            out.writeFloat(frameStrokeWeight)
            out.writeFloat(guideStrokeWeight)
            out.writeInt(if (isCropEnabled) 1 else 0)
            out.writeInt(handleColor)
            out.writeInt(guideColor)
            out.writeFloat(initialFrameScale)
            out.writeFloat(angle)
            out.writeInt(if (isAnimationEnabled) 1 else 0)
            out.writeInt(animationDuration)
            out.writeInt(exifRotation)
            out.writeParcelable(sourceUri, flag)
            out.writeParcelable(saveUri, flag)
            out.writeSerializable(compressFormat)
            out.writeInt(compressQuality)
            out.writeInt(if (isDebug) 1 else 0)
            out.writeInt(outputMaxWidth)
            out.writeInt(outputMaxHeight)
            out.writeInt(outputWidth)
            out.writeInt(outputHeight)
            out.writeInt(if (isHandleShadowEnabled) 1 else 0)
            out.writeInt(inputImageWidth)
            out.writeInt(inputImageHeight)
            out.writeInt(outputImageWidth)
            out.writeInt(outputImageHeight)
        }

        companion object {

            val CREATOR: Creator<*> = object : Creator<Any?> {
                override fun createFromParcel(inParcel: Parcel): SavedState {
                    return SavedState(inParcel)
                }

                override fun newArray(inSize: Int): Array<SavedState?> {
                    return arrayOfNulls(inSize)
                }
            }
        }
    }

    companion object {
        private val TAG = CropImageView::class.java.simpleName

        // Constants ///////////////////////////////////////////////////////////////////////////////////
        private const val HANDLE_SIZE_IN_DP = 14
        private const val MIN_FRAME_SIZE_IN_DP = 50
        private const val FRAME_STROKE_WEIGHT_IN_DP = 1
        private const val GUIDE_STROKE_WEIGHT_IN_DP = 1
        private const val DEFAULT_INITIAL_FRAME_SCALE = 1f
        private const val DEFAULT_ANIMATION_DURATION_MILLIS = 100
        private const val DEBUG_TEXT_SIZE_IN_DP = 15
        private const val TRANSPARENT = 0x00000000
        private const val TRANSLUCENT_WHITE = -0x44000001
        private const val WHITE = -0x1
        private const val TRANSLUCENT_BLACK = -0x45000000
    }

    // Constructor /////////////////////////////////////////////////////////////////////////////////
    init {
        mExecutor = Executors.newSingleThreadExecutor()
        val density = density
        mHandleSize = (density * HANDLE_SIZE_IN_DP).toInt()
        mMinFrameSize = density * MIN_FRAME_SIZE_IN_DP
        mFrameStrokeWeight = density * FRAME_STROKE_WEIGHT_IN_DP
        mGuideStrokeWeight = density * GUIDE_STROKE_WEIGHT_IN_DP
        mPaintFrame = Paint()
        mPaintTranslucent = Paint()
        mPaintBitmap = Paint()
        mPaintBitmap.isFilterBitmap = true
        mPaintDebug = Paint()
        mPaintDebug.isAntiAlias = true
        mPaintDebug.style = Paint.Style.STROKE
        mPaintDebug.color = WHITE
        mPaintDebug.textSize = DEBUG_TEXT_SIZE_IN_DP.toFloat() * density
        mMatrix = Matrix()
        mScale = 1.0f
        mBackgroundColor = TRANSPARENT
        mFrameColor = WHITE
        mOverlayColor = TRANSLUCENT_BLACK
        mHandleColor = WHITE
        mGuideColor = TRANSLUCENT_WHITE

        // handle Styleable
        handleStyleable(context, attrs, defStyle, density)
    }
}
