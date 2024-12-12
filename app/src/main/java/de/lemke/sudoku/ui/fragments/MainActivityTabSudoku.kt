package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SeslSeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.skydoves.transformationlayout.TransformationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabSudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.SudokuLevelActivity
import de.lemke.sudoku.ui.utils.ButtonUtils
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabSudoku : Fragment(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: FragmentTabSudokuBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getRecentSudoku: GetRecentlyUpdatedNormalSudokuUseCase

    @Inject
    lateinit var isDailySudokuCompleted: IsDailySudokuCompletedUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabSudokuBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        val loadingDialog = ProgressDialog(context)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        binding.newSudokuLayout.translateYWithAppBar(activity.findViewById<DrawerLayout>(R.id.drawerLayout).appBarLayout, this)

        binding.sizeSeekbar.setSeamless(true)
        binding.difficultySeekbar.setSeamless(true)
        binding.difficultySeekbar.max = Difficulty.max
        binding.newGameButton.setOnClickListener {
            loadingDialog.show()
            lifecycleScope.launch {
                val sudoku = generateSudoku(binding.sizeSeekbar.size, Difficulty.fromInt(binding.difficultySeekbar.progress))
                saveSudoku(sudoku)
                TransformationCompat.startActivity(
                    binding.newGameTransformationLayout,
                    Intent(activity, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                )
                loadingDialog.dismiss()
            }
        }
        binding.dailyButton.setOnClickListener {
            //TransformationCompat.startActivity(binding.dailyTransformationLayout, Intent(activity, DailySudokuActivity::class.java))
            //cant use bc transitionNames should be unique within the view hierarchy.
            val bundle = binding.dailyTransformationLayout.withActivity(requireActivity(), "DailySudokuActivityTransition")
            val intent = Intent(requireContext(), DailySudokuActivity::class.java)
            intent.putExtra("com.skydoves.transformationlayout", binding.dailyTransformationLayout.getParcelableParams())
            startActivity(intent, bundle)
        }
        binding.levelsButton.setOnClickListener {
            //TransformationCompat.startActivity(binding.levelsTransformationLayout, Intent(activity, SudokuLevelActivity::class.java))
            //cant use bc transitionNames should be unique within the view hierarchy.
            val bundle = binding.levelsTransformationLayout.withActivity(requireActivity(), "SudokuLevelActivityTransition")
            val intent = Intent(requireContext(), SudokuLevelActivity::class.java)
            intent.putExtra("com.skydoves.transformationlayout", binding.levelsTransformationLayout.getParcelableParams())
            startActivity(intent, bundle)
        }
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            binding.difficultySeekbar.progress = userSettings.difficultySliderValue
            binding.difficultySeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(difficultySliderValue = progress) } }
                }
            })
            binding.sizeSeekbar.progress = userSettings.sizeSliderValue
            binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(sizeSliderValue = progress) } }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val sudoku = getRecentSudoku()
            if (sudoku != null && !sudoku.completed && !sudoku.errorLimitReached(getUserSettings().errorLimit)) {
                binding.continueTransformationLayout.visibility = View.VISIBLE
                binding.continueGameButton.text = getString(
                    R.string.continue_game,
                    sudoku.sizeString,
                    sudoku.difficulty.getLocalString(resources)
                )
                binding.continueGameButton.setOnClickListener {
                    TransformationCompat.startActivity(
                        binding.continueTransformationLayout,
                        Intent(activity, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                    )
                }
            } else binding.continueTransformationLayout.visibility = View.GONE
            ButtonUtils.setButtonStyle(
                requireContext(),
                binding.dailyButton,
                if (isDailySudokuCompleted()) R.style.ButtonStyle_Filled_Themed else R.style.ButtonStyle_Colored
            )
        }
    }
}

private val SeslSeekBar.size: Int
    get() = when (this.progress) {
        0 -> 4
        1 -> 9
        2 -> 16
        else -> 9
    }
