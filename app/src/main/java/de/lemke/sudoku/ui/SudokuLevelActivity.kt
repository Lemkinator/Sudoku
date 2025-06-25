package de.lemke.sudoku.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.skydoves.bundler.intentOf
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.prepareActivityTransformationBetween
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySudokuLevelBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
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
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        binding.viewPagerLevel.adapter = ViewPager2AdapterTabLevelSubtabs(this)
        binding.viewPagerLevel.offscreenPageLimit = 2
        TabLayoutMediator(binding.fragmentLevelSubTabs, binding.viewPagerLevel) { tab, position ->
            tab.text = arrayOf(getString(R.string.size4), getString(R.string.size9), getString(R.string.size16))[position]
        }.attach()
        lifecycleScope.launch {
            binding.viewPagerLevel.setCurrentItem(getUserSettings().currentLevelTab, false)
            binding.viewPagerLevel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {}
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                override fun onPageSelected(position: Int) {
                    lifecycleScope.launch { updateUserSettings { it.copy(currentLevelTab = position) } }
                }
            })
        }
    }
}

class ViewPager2AdapterTabLevelSubtabs(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SudokuLevelTab().apply { arguments = intentOf { +("size" to 4) }.extras }
        1 -> SudokuLevelTab().apply { arguments = intentOf { +("size" to 9) }.extras }
        2 -> SudokuLevelTab().apply { arguments = intentOf { +("size" to 16) }.extras }
        else -> SudokuLevelTab().apply { arguments = intentOf { +("size" to 9) }.extras }
    }
}
