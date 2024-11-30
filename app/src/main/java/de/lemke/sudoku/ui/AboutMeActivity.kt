package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.gms.games.PlayGames
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityAboutMeBinding
import de.lemke.sudoku.domain.OpenAppUseCase
import de.lemke.sudoku.domain.OpenLinkUseCase
import de.lemke.sudoku.domain.setCustomBackPressAnimation
import dev.oneuiproject.oneui.ktx.isInMultiWindowModeCompat
import dev.oneuiproject.oneui.ktx.semSetToolTipText
import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AboutMeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutMeBinding
    private val appBarListener: AboutAppBarListener = AboutAppBarListener()

    @Inject
    lateinit var openLink: OpenLinkUseCase

    @Inject
    lateinit var openApp: OpenAppUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)

        applyInsetIfNeeded()
        setupToolbar()

        initContent()
        refreshAppBar(resources.configuration)
        setupOnClickListeners()
    }

    private fun applyInsetIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30 && !window.decorView.fitsSystemWindows) {
            binding.root.setOnApplyWindowInsetsListener { _, insets ->
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.root.setPadding(
                    systemBarsInsets.left, systemBarsInsets.top,
                    systemBarsInsets.right, systemBarsInsets.bottom
                )
                insets
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.aboutToolbar)
        //Should be called after setSupportActionBar
        binding.aboutToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun refreshAppBar(config: Configuration) {
        ToolbarLayoutUtils.hideStatusBarForLandscape(this, config.orientation)
        ToolbarLayoutUtils.updateListBothSideMargin(this, binding.aboutBottomContainer)
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowModeCompat) {
            binding.aboutAppBar.apply {
                seslSetCustomHeightProportion(true, 0.5f)//expanded
                addOnOffsetChangedListener(appBarListener)
                setExpanded(true, false)
            }
            binding.aboutSwipeUpContainer.apply {
                updateLayoutParams { height = resources.displayMetrics.heightPixels / 2 }
                visibility = View.VISIBLE
            }
        } else {
            binding.aboutAppBar.apply {
                setExpanded(false, false)
                seslSetCustomHeightProportion(true, 0f)
                removeOnOffsetChangedListener(appBarListener)
            }
            binding.aboutBottomContainer.alpha = 1f
            binding.aboutSwipeUpContainer.visibility = View.GONE
            setBottomContentEnabled(true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAppBar(newConfig)
    }

    private fun initContent() {
        val appIcon = AppCompatResources.getDrawable(this, R.drawable.me4_round)
        binding.aboutHeaderIcon.setImageDrawable(appIcon)
        binding.aboutBottomIcon.setImageDrawable(appIcon)
        binding.aboutHeaderGithub.semSetToolTipText(getString(R.string.github))
        binding.aboutHeaderPlayStore.semSetToolTipText(getString(R.string.playstore))
        binding.aboutHeaderWebsite.semSetToolTipText(getString(R.string.website))
        binding.aboutHeaderInsta.semSetToolTipText(getString(R.string.instagram))
        binding.aboutHeaderTiktok.semSetToolTipText(getString(R.string.tiktok))
    }

    private fun setBottomContentEnabled(enabled: Boolean) {
        binding.aboutHeaderGithub.isEnabled = !enabled
        binding.aboutHeaderWebsite.isEnabled = !enabled
        binding.aboutHeaderPlayStore.isEnabled = !enabled
        binding.aboutHeaderInsta.isEnabled = !enabled
        binding.aboutHeaderTiktok.isEnabled = !enabled
        binding.aboutBottomContent.aboutBottomRateApp.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomShareApp.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomWriteEmail.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativeTiktok.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativeWebsite.isEnabled = enabled
        binding.aboutBottomContent.aboutBottomRelativePlayStore.isEnabled = enabled
    }

    private fun openPlayStore() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ad))
            .setMessage(getString(R.string.playstore_redirect_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                openLink(getString(R.string.playstore_developer_page_link))
            }
            .setNegativeButton(getString(R.string.sesl_cancel), null)
            .show()
    }

    private fun setupOnClickListeners() {
        binding.aboutHeaderGithub.setOnClickListener { openLink(getString(R.string.my_github)) }
        binding.aboutHeaderPlayStore.setOnClickListener { openPlayStore() }
        binding.aboutHeaderWebsite.setOnClickListener { openLink(getString(R.string.my_website)) }
        binding.aboutHeaderInsta.setOnClickListener { openLink(getString(R.string.my_insta)) }
        binding.aboutHeaderTiktok.setOnClickListener { openLink(getString(R.string.rick_roll_troll_link)) }
        with(binding.aboutBottomContent) {
            aboutBottomRelativePlayStore.setOnClickListener { openPlayStore() }
            aboutBottomRelativeWebsite.setOnClickListener { openLink(getString(R.string.my_website)) }
            aboutBottomRelativeTiktok.setOnClickListener { openLink(getString(R.string.rick_roll_troll_link)) }
            aboutBottomRateApp.setOnClickListener { openApp(packageName, false) }
            aboutBottomShareApp.setOnClickListener {
                PlayGames.getAchievementsClient(this@AboutMeActivity).unlock(getString(R.string.achievement_share_app))
                startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.playstore_link) + packageName)
                }, null))
            }
            aboutBottomWriteEmail.setOnClickListener {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse("mailto:") // only email apps should handle this
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email)))
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                intent.putExtra(Intent.EXTRA_TEXT, "")
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    Toast.makeText(this@AboutMeActivity, getString(R.string.no_email_app_installed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class AboutAppBarListener : OnOffsetChangedListener {
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
                binding.aboutSwipeUpContainer.alpha = (1 - offsetAlpha * -3).coerceIn(0f, 1f)
            }
            // Handle the bottom part of the UI
            val alphaRange = binding.aboutCtl.height * 0.143f
            val layoutPosition = abs(appBarLayout.top).toFloat()
            val bottomAlpha = (150.0f / alphaRange * (layoutPosition - binding.aboutCtl.height * 0.35f)).coerceIn(0f, 255f)
            binding.aboutBottomContainer.alpha = bottomAlpha / 255
        }
    }
}