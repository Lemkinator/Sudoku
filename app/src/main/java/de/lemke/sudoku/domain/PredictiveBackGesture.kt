package de.lemke.sudoku.domain

import android.annotation.SuppressLint
import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslMisc
import androidx.core.view.animation.PathInterpolatorCompat
import de.lemke.sudoku.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interpolator for gesture animations.
 */
val GestureInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)

/**
 * Provides an outline for back animation with rounded corners.
 */
class BackAnimationOutlineProvider() : ViewOutlineProvider() {
    /**
     * The radius of the rounded corners.
     */
    var radius = 0f

    /**
     * The progress of the animation, which sets the radius.
     */
    var progress: Float = 0f
        set(value) {
            radius = value * 100f
        }

    /**
     * Sets the outline of the view with rounded corners based on the radius.
     */
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}

/**
 * Sets custom animated onBackPressed logic with optional back press logic enabled state.
 *
 * @param animatedView The view to animate.
 * @param backPressLogicEnabled Optional Boolean to enable or disable custom back press logic.
 * @param onBackPressedLogic Lambda to be invoked for custom onBackPressed logic.
 */
inline fun AppCompatActivity.setCustomAnimatedOnBackPressedLogic(
    animatedView: View,
    backPressLogicEnabled: Boolean,
    crossinline onBackPressedLogic: () -> Unit = {}
) = setCustomAnimatedOnBackPressedLogic(animatedView, MutableStateFlow(backPressLogicEnabled), onBackPressedLogic)

/**
 * Sets custom back press animation for the given view.
 *
 * @param animatedView The view to animate.
 */
fun AppCompatActivity.setCustomBackPressAnimation(animatedView: View) = setCustomAnimatedOnBackPressedLogic(animatedView)

/**
 * Sets custom animated onBackPressed logic with optional back press logic enabled state.
 *
 * @param animatedView The view to animate.
 * @param backPressLogicEnabled Optional StateFlow to enable or disable custom back press logic.
 * @param onBackPressedLogic Lambda to be invoked for custom onBackPressed logic.
 */

inline fun AppCompatActivity.setCustomAnimatedOnBackPressedLogic(
    animatedView: View,
    backPressLogicEnabled: StateFlow<Boolean>? = null,
    crossinline onBackPressedLogic: () -> Unit = {}
) {
    val predictiveBackMargin = resources.getDimension(R.dimen.predictive_back_margin)
    var initialTouchY = -1f
    var outlineProvider = BackAnimationOutlineProvider()
    animatedView.clipToOutline = true
    animatedView.outlineProvider = outlineProvider
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressLogicEnabled?.value == true) {
                    onBackPressedLogic.invoke()
                } else {
                    finishAfterTransition()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (backPressLogicEnabled?.value == true) return
                val progress = GestureInterpolator.getInterpolation(backEvent.progress)
                if (initialTouchY < 0f) {
                    initialTouchY = backEvent.touchY
                }
                val progressY = GestureInterpolator.getInterpolation(
                    (backEvent.touchY - initialTouchY) / animatedView.height
                )

                // See the motion spec about the calculations below.
                // https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#motion-specs

                // Shift horizontally.
                val maxTranslationX = (animatedView.width / 20) - predictiveBackMargin
                animatedView.translationX = progress * maxTranslationX *
                        (if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1)

                // Shift vertically.
                val maxTranslationY = (animatedView.height / 20) - predictiveBackMargin
                animatedView.translationY = progressY * maxTranslationY

                // Scale down from 100% to 90%.
                val scale = 1f - (0.1f * progress)
                animatedView.scaleX = scale
                animatedView.scaleY = scale

                // apply rounded corners
                outlineProvider.progress = progress
                animatedView.invalidateOutline()
            }

            override fun handleOnBackCancelled() {
                initialTouchY = -1f
                animatedView.run {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
        }
    )
}

fun AppCompatActivity.setWindowTransparent(transparent: Boolean) {
    window.apply {
        if (transparent) {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent_window_bg_color)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setTranslucent(true)
            }
        } else {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(defaultWindowBackground)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setTranslucent(false)
            }
        }
    }
}

val AppCompatActivity.defaultWindowBackground: Int
    @SuppressLint("RestrictedApi", "PrivateResource")
    get() = if (SeslMisc.isLightTheme(this)) {
        androidx.appcompat.R.color.sesl_round_and_bgcolor_light
    } else {
        androidx.appcompat.R.color.sesl_round_and_bgcolor_dark
    }
