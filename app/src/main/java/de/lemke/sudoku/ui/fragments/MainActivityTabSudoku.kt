package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SeslSeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabSudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.SudokuActivity
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.utils.SeekBarUtils
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import dev.oneuiproject.oneui.widget.HapticSeekBar
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivityTabSudoku : Fragment() {
    private lateinit var binding: FragmentTabSudokuBinding
    private lateinit var toolbarLayout: ToolbarLayout
    private var preloadedSudokus: List<Sudoku>? = null

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    @Inject
    lateinit var preloadSudokus: PreloadSudokusUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getRecentSudoku: GetRecentSudokuUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabSudokuBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        toolbarLayout = activity.findViewById(R.id.main_toolbarlayout)
        toolbarLayout.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.newSudokuLayout.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.newSudokuLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }
        SeekBarUtils.showTickMark(binding.difficultySeekbar, true)
        binding.difficultySeekbar.setSeamless(true)
        binding.difficultySeekbar.max = 4
        binding.newGameButton.setOnClickListener {
            val mLoadingDialog = ProgressDialog(context)
            mLoadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
            mLoadingDialog.setCancelable(false)
            mLoadingDialog.show()
            lifecycleScope.launch {
                if (preloadedSudokus != null) {
                    Log.d("MainActivityTabSudoku", "Using preloaded sudokus")
                    val sudoku = preloadedSudokus!![binding.difficultySeekbar.progress]
                    saveSudoku(sudoku)
                    startActivity(Intent(activity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                } else {
                    Log.d("MainActivityTabSudoku", "generating new sudoku")
                    val sudoku = generateSudoku(9, Difficulty.fromInt(binding.difficultySeekbar.progress))
                    saveSudoku(sudoku)
                    startActivity(Intent(activity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                }
                mLoadingDialog.dismiss()
                preloadedSudokus = null
                preloadedSudokus = preloadSudokus()
            }
        }
        lifecycleScope.launch {
            val sliderValue = getUserSettings().difficultySliderValue
            binding.difficultySeekbar.progress = sliderValue
            binding.newGameButton.text = getString(R.string.new_game, Difficulty.getLocalString(sliderValue, resources))
            binding.difficultySeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    binding.newGameButton.text = getString(R.string.new_game, Difficulty.getLocalString(progress, resources))
                    lifecycleScope.launch { updateUserSettings { it.copy(difficultySliderValue = progress) } }
                }

                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
            })
            preloadedSudokus = preloadSudokus()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val sudoku = getRecentSudoku()
            if (sudoku != null && !sudoku.completed) {
                binding.continueGameButton.visibility = View.VISIBLE
                binding.continueGameButton.text = getString(
                    R.string.continue_game,
                    sudoku.difficulty.getLocalString(resources),
                    sudoku.timeString
                )
                binding.continueGameButton.setOnClickListener {
                    startActivity(Intent(activity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                }
            } else binding.continueGameButton.visibility = View.GONE
        }
    }

}