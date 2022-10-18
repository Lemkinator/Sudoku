package de.lemke.sudoku.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityAboutMeBinding
import de.lemke.sudoku.domain.OpenAppUseCase
import dev.oneuiproject.oneui.layout.DrawerLayout
import javax.inject.Inject
/*
@AndroidEntryPoint
class AboutMeActivityOld : AppCompatActivity() {

    private lateinit var binding: ActivityAboutMeBinding
    @Inject
    lateinit var openApp: OpenAppUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.supportMeDrawerLayout.setNavigationButtonIcon(AppCompatResources.getDrawable(this, dev.oneuiproject.oneui.R.drawable.ic_oui_back))
        binding.supportMeDrawerLayout.setNavigationButtonOnClickListener { finish() }

        binding.websiteButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.my_website))))
        }
        binding.ticktocktrollButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.rick_roll_troll_link)))) //Rick Roll :D
        }
        binding.supportMeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:") // only email apps should handle this
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email)))
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            intent.putExtra(Intent.EXTRA_TEXT, "")
            try {
                startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(this@AboutMeActivityOld, getString(R.string.no_email_app_installed), Toast.LENGTH_SHORT).show()
            }
        }
        binding.reviewCommentButton.setOnClickListener {
            AlertDialog.Builder(this@AboutMeActivityOld)
                .setTitle(getString(R.string.write_review))
                .setMessage(getString(R.string.review_comment))
                .setNeutralButton(R.string.ok, null)
                .setPositiveButton(R.string.to_play_store) { _, _ -> openApp(packageName, false)}
                .show()
        }
        binding.writeReviewButton.setOnClickListener {
            val manager = ReviewManagerFactory.create(this@AboutMeActivityOld)
            //val manager = FakeReviewManager(context);
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener { task2 ->
                        if (task2.isSuccessful) Log.d("AboutActivity", "Reviewtask was successful")
                        else Toast.makeText(this@AboutMeActivityOld, getString(R.string.error) + ": " + task2.exception, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // There was some problem, log or handle the error code.
                    Toast.makeText(this@AboutMeActivityOld, R.string.task_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.shareAppButton.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text) + packageName)
            sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_app))
            startActivity(Intent.createChooser(sendIntent, "Share Via"))
        }
    }
}*/