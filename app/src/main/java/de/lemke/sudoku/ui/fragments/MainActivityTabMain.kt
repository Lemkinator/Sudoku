package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.model.Difficulty
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.utils.SeekBarUtils
import dev.oneuiproject.oneui.widget.HapticSeekBar
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabMain : Fragment() {
    private lateinit var rootView: View
    private lateinit var toolbarLayout: ToolbarLayout
    private var mainMenuLayout: LinearLayout? = null
    private lateinit var difficultySeekbar: HapticSeekBar


    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_tab_main, container, false)
        return rootView
    }

    @SuppressLint("RtlHardcoded")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        difficultySeekbar = rootView.findViewById(R.id.difficulty_seekbar)
        rootView.findViewById<AppCompatButton>(R.id.new_game_button).setOnClickListener {
            val size = 9
            val mLoadingDialog = ProgressDialog(context)
            mLoadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
            mLoadingDialog.setCancelable(false)
            mLoadingDialog.show()
            lifecycleScope.launch {
                val game = generateSudoku(size, Difficulty.fromInt(difficultySeekbar.progress))

                mLoadingDialog.dismiss()
            }
        }
        rootView.findViewById<AppCompatButton>(R.id.continue_game_button)
        SeekBarUtils.showTickMark(difficultySeekbar, true)
        difficultySeekbar.setSeamless(true)
        difficultySeekbar.max = 4
        difficultySeekbar.progress = difficultySeekbar.max / 2
    }

}