package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityAboutMeBinding
import de.lemke.sudoku.databinding.ActivityAboutMeContentBinding
import dev.oneuiproject.oneui.utils.ViewUtils
import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils
import kotlin.math.abs

@AndroidEntryPoint
class AboutMeActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityAboutMeBinding
    private lateinit var bottomContent: ActivityAboutMeContentBinding
    private var enableBackToHeader = false
    private var lastClickTime: Long = 0
    private val appBarListener: AboutMeActivity.AboutMeAppBarListener = AboutMeAppBarListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bottomContent = binding.aboutBottomContent
        setSupportActionBar(binding.aboutToolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        resetAppBar(resources.configuration)
        initContent()
        initOnBackPressed()
    }

    private fun initOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (enableBackToHeader && binding.aboutAppBar.seslIsCollapsed()) binding.aboutAppBar.setExpanded(true)
                else finish()
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetAppBar(newConfig)
    }

    @SuppressLint("RestrictedApi")
    private fun resetAppBar(config: Configuration) {
        ToolbarLayoutUtils.hideStatusBarForLandscape(this, config.orientation)
        ToolbarLayoutUtils.updateListBothSideMargin(this, binding.aboutBottomContainer)
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode) {
            binding.aboutAppBar.seslSetCustomHeightProportion(true, 0.5f)
            enableBackToHeader = true
            binding.aboutAppBar.addOnOffsetChangedListener(appBarListener)
            binding.aboutAppBar.setExpanded(true, false)
            binding.aboutSwipeUpContainer.visibility = View.VISIBLE
            val lp: ViewGroup.LayoutParams = binding.aboutSwipeUpContainer.layoutParams
            lp.height = resources.displayMetrics.heightPixels / 2
        } else {
            binding.aboutAppBar.setExpanded(false, false)
            enableBackToHeader = false
            binding.aboutAppBar.seslSetCustomHeightProportion(true, 0F)
            binding.aboutAppBar.removeOnOffsetChangedListener(appBarListener)
            binding.aboutBottomContainer.alpha = 1f
            setBottomContentEnabled(true)
            binding.aboutSwipeUpContainer.visibility = View.GONE
        }
    }

    private fun initContent() {
        ViewUtils.semSetRoundedCorners(
            binding.aboutBottomContent.root,
            ViewUtils.SEM_ROUNDED_CORNER_TOP_LEFT or ViewUtils.SEM_ROUNDED_CORNER_TOP_RIGHT
        )
        ViewUtils.semSetRoundedCornerColor(
            binding.aboutBottomContent.root,
            ViewUtils.SEM_ROUNDED_CORNER_TOP_LEFT or ViewUtils.SEM_ROUNDED_CORNER_TOP_RIGHT,
            getColor(dev.oneuiproject.oneui.R.color.oui_round_and_bgcolor)
        )
        val appIcon = getDrawable(R.drawable.me4_round)
        binding.aboutHeaderIcon.setImageDrawable(appIcon)
        binding.aboutBottomIcon.setImageDrawable(appIcon)
        binding.aboutHeaderGithub.setOnClickListener(this)
        binding.aboutHeaderWebsite.setOnClickListener(this)
        binding.aboutHeaderEmail.setOnClickListener(this)
        binding.aboutHeaderTiktok.setOnClickListener(this)
        TooltipCompat.setTooltipText(binding.aboutHeaderGithub, getString(R.string.github))
        bottomContent.aboutBottomRateApp.setOnClickListener(this)
        bottomContent.aboutBottomRelativeWebsite.setOnClickListener(this)
    }

    private fun setBottomContentEnabled(enabled: Boolean) {
        binding.aboutHeaderGithub.isEnabled = !enabled
        binding.aboutHeaderWebsite.isEnabled = !enabled
        binding.aboutHeaderEmail.isEnabled = !enabled
        binding.aboutHeaderTiktok.isEnabled = !enabled
        bottomContent.aboutBottomRateApp.isEnabled = enabled
        bottomContent.aboutBottomRelativeWebsite.isEnabled = enabled
    }

    override fun onClick(v: View) {
        val uptimeMillis = SystemClock.uptimeMillis()
        if (uptimeMillis - lastClickTime > 600L) {
            when (v.id) {
                binding.aboutHeaderGithub.id -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.my_github))))
                }
                binding.aboutHeaderWebsite.id -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.my_website))))
                }
                binding.aboutHeaderEmail.id -> {
                    TODO()
                }
                binding.aboutHeaderTiktok.id -> {
                    TODO()
                }
                bottomContent.aboutBottomRelativeWebsite.id -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.my_website))))
                }
                bottomContent.aboutBottomRateApp.id -> {}
            }
        }
        lastClickTime = uptimeMillis
    }

    // kang from com.sec.android.app.launcher
    private inner class AboutMeAppBarListener : OnOffsetChangedListener {
        override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
            // Handle the SwipeUp anim view
            val totalScrollRange = appBarLayout.totalScrollRange
            val abs = abs(verticalOffset)
            if (abs >= totalScrollRange / 2) {
                binding.aboutSwipeUpContainer.alpha = 0f
                setBottomContentEnabled(true)
            } else if (abs == 0) {
                binding.aboutSwipeUpContainer.alpha = 1f
                setBottomContentEnabled(false)
            } else {
                val offsetAlpha = appBarLayout.y / totalScrollRange
                var arrowAlpha = 1 - offsetAlpha * -3
                if (arrowAlpha < 0) {
                    arrowAlpha = 0f
                } else if (arrowAlpha > 1) {
                    arrowAlpha = 1f
                }
                binding.aboutSwipeUpContainer.alpha = arrowAlpha
            }

            // Handle the bottom part of the UI
            val alphaRange: Float = binding.aboutCtl.height * 0.143f
            val layoutPosition = abs(appBarLayout.top).toFloat()
            var bottomAlpha: Float = (150.0f / alphaRange * (layoutPosition - binding.aboutCtl.height * 0.35f))
            if (bottomAlpha < 0) {
                bottomAlpha = 0f
            } else if (bottomAlpha >= 255) {
                bottomAlpha = 255f
            }
            binding.aboutBottomContainer.alpha = bottomAlpha / 255
        }
    }
}