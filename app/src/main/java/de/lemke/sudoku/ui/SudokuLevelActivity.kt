/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.prepareActivityTransformationBetween
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.sudoku.databinding.ActivitySudokuLevelBinding
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.fragments.SudokuLevelTab

@AndroidEntryPoint
class SudokuLevelActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuLevelBinding
    private val viewModel: SudokuLevelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        binding.viewPagerLevel.adapter = ViewPager2AdapterTabLevelSubtabs(this)
        binding.viewPagerLevel.offscreenPageLimit = 2
        TabLayoutMediator(binding.fragmentLevelSubTabs, binding.viewPagerLevel) { tab, position ->
            tab.text = SudokuSize.entries[position].getLocalString(resources)
        }.attach()
        binding.viewPagerLevel.setCurrentItem(viewModel.currentLevelTab, false)
        binding.viewPagerLevel.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewModel.currentLevelTab = position
                }
            },
        )
    }
}

class ViewPager2AdapterTabLevelSubtabs(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = SudokuSize.entries.size

    override fun createFragment(position: Int): Fragment = SudokuLevelTab.newInstance(SudokuSize.entries[position])
}
