package de.lemke.sudoku.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySudokuLevelBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.setCustomBackPressAnimation
import de.lemke.sudoku.domain.setWindowTransparent
import de.lemke.sudoku.ui.fragments.SudokuLevelTab
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SudokuLevelActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuLevelBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        binding.sudokuLevelToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.sudokuLevelToolbarLayout.setNavigationButtonOnClickListener { finishAfterTransition() }
        binding.fragmentLevelSubTabs.seslSetSubTabStyle()
        binding.fragmentLevelSubTabs.tabMode = TabLayout.SESL_MODE_WEIGHT_AUTO
        binding.viewPagerLevel.adapter = ViewPager2AdapterTabLevelSubtabs(this)
        TabLayoutMediator(binding.fragmentLevelSubTabs, binding.viewPagerLevel) { tab, position ->
            tab.text = arrayOf(getString(R.string.size4), getString(R.string.size9), getString(R.string.size16))[position]
        }.attach()
        lifecycleScope.launch {
            binding.viewPagerLevel.setCurrentItem(getUserSettings().currentLevelTab, false)
            binding.viewPagerLevel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                override fun onPageSelected(position: Int) {
                    lifecycleScope.launch { updateUserSettings { it.copy(currentLevelTab = position) } }
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
        }
        setCustomBackPressAnimation(binding.root)
    }
}

class ViewPager2AdapterTabLevelSubtabs(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SudokuLevelTab(4)
        1 -> SudokuLevelTab(9)
        2 -> SudokuLevelTab(16)
        else -> SudokuLevelTab(9)
    }
}
