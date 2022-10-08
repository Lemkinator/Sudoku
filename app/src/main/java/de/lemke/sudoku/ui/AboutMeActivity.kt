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
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.OpenAppUseCase
import dev.oneuiproject.oneui.layout.DrawerLayout
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutMeActivity : AppCompatActivity() {

    @Inject
    lateinit var openApp: OpenAppUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_me)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_support_me)
        drawerLayout.setNavigationButtonIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_oui_back_24))
        drawerLayout.setNavigationButtonOnClickListener { onBackPressed() }

        findViewById<View>(R.id.websiteButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.my_website))))
        }
        findViewById<View>(R.id.ticktocktrollButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.rick_roll_troll_link)))) //Rick Roll :D
        }
        findViewById<View>(R.id.supportMeButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:") // only email apps should handle this
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email)))
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            intent.putExtra(Intent.EXTRA_TEXT, "")
            try {
                startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(this@AboutMeActivity, getString(R.string.no_email_app_installed), Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.reviewCommentButton).setOnClickListener {
            AlertDialog.Builder(this@AboutMeActivity)
                .setTitle(getString(R.string.write_review))
                .setMessage(getString(R.string.review_comment))
                .setNeutralButton(R.string.ok, null)
                .setPositiveButton(R.string.to_play_store) { _, _ -> openApp(packageName, false)}
                .show()
        }
        findViewById<View>(R.id.writeReviewButton).setOnClickListener {
            val manager = ReviewManagerFactory.create(this@AboutMeActivity)
            //val manager = FakeReviewManager(context);
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener { task2 ->
                        if (task2.isSuccessful) Log.d("AboutActivity", "Reviewtask was successful")
                        else Toast.makeText(this@AboutMeActivity, getString(R.string.error) + ": " + task2.exception, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // There was some problem, log or handle the error code.
                    Toast.makeText(this@AboutMeActivity, R.string.task_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        findViewById<View>(R.id.shareAppButton).setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text) + packageName)
            sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_app))
            startActivity(Intent.createChooser(sendIntent, "Share Via"))
        }
    }
}