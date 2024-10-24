package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabSudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuLevelActivity
import de.lemke.sudoku.ui.utils.ButtonUtils
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivityTabSudoku : Fragment() {
    private lateinit var binding: FragmentTabSudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private var onOffsetChangedListener: AppBarLayout.OnOffsetChangedListener? = null

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
        loadingDialog = ProgressDialog(context)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        onOffsetChangedListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.newSudokuLayout.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.newSudokuLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }

        activity.findViewById<DrawerLayout>(R.id.drawer_layout_main).appBarLayout.addOnOffsetChangedListener(onOffsetChangedListener)
        binding.sizeSeekbar.setSeamless(true)
        binding.difficultySeekbar.setSeamless(true)
        binding.difficultySeekbar.max = Difficulty.max
        binding.newGameButton.setOnClickListener {
            loadingDialog.show()
            lifecycleScope.launch {
                val sudoku = generateSudoku(binding.sizeSeekbar.size, Difficulty.fromInt(binding.difficultySeekbar.progress))
                saveSudoku(sudoku)
                startActivity(Intent(activity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                loadingDialog.dismiss()
            }
        }
        binding.dailyButton.setOnClickListener {
            startActivity(Intent(activity, DailySudokuActivity::class.java))
        }
        binding.levelsButton.setOnClickListener {
            startActivity(Intent(activity, SudokuLevelActivity::class.java))
        }
        lifecycleScope.launch {
            val sliderValue = getUserSettings().difficultySliderValue
            binding.difficultySeekbar.progress = sliderValue
            binding.difficultySeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(difficultySliderValue = progress) } }
                }

                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
            })
            val sizeValue = getUserSettings().sizeSliderValue
            binding.sizeSeekbar.progress = sizeValue
            binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(sizeSliderValue = progress) } }
                }

                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
            })
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val sudoku = getRecentSudoku()
            if (sudoku != null && !sudoku.completed && !sudoku.errorLimitReached(getUserSettings().errorLimit)) {
                binding.continueGameButton.visibility = View.VISIBLE
                binding.continueGameButton.text = getString(
                    R.string.continue_game,
                    sudoku.sizeString,
                    sudoku.difficulty.getLocalString(resources)
                )
                binding.continueGameButton.setOnClickListener {
                    startActivity(Intent(activity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                }
            } else binding.continueGameButton.visibility = View.GONE

            ButtonUtils.setButtonStyle(
                requireContext(),
                binding.dailyButton,
                if (isDailySudokuCompleted()) R.style.ButtonStyle_Filled else R.style.ButtonStyle_Colored
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout_main).appBarLayout
            .removeOnOffsetChangedListener(onOffsetChangedListener)
    }
}

private val SeslSeekBar.size: Int
    get() = when (this.progress) {
        0 -> 4
        1 -> 9
        2 -> 16
        else -> 9
    }
