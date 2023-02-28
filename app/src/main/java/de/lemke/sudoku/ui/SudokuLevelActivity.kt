package de.lemke.sudoku.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySudokuLevelBinding
import de.lemke.sudoku.ui.fragments.SudokuLevelTab

@AndroidEntryPoint
class SudokuLevelActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuLevelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.sudokuLevelToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.sudokuLevelToolbarLayout.setNavigationButtonOnClickListener { finish() }
        binding.fragmentLevelSubTabs.seslSetSubTabStyle()
        binding.fragmentLevelSubTabs.tabMode = TabLayout.SESL_MODE_WEIGHT_AUTO
        binding.viewPagerLevel.adapter = ViewPager2AdapterTabLevelSubtabs(this)
        binding.viewPagerLevel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
        })
        TabLayoutMediator(binding.fragmentLevelSubTabs, binding.viewPagerLevel) { tab, position ->
            tab.text = arrayOf(getString(R.string.size4), getString(R.string.size9), getString(R.string.size16))[position]
        }.attach()
        binding.viewPagerLevel.setCurrentItem(1, false)
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.fragments.forEach { (it as OnDataChangedListener).onDataChanged() }
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

interface OnDataChangedListener {
    fun onDataChanged()
}
