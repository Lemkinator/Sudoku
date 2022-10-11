package de.lemke.sudoku.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty

@AndroidEntryPoint
class MainActivityTabStatistics : Fragment() {
    private lateinit var rootView: View
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_tab_statistics, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val subTabs: TabLayout = rootView.findViewById(R.id.fragment_statistics_sub_tabs)
        subTabs.seslSetSubTabStyle()
        subTabs.tabMode = TabLayout.SESL_MODE_WEIGHT_AUTO
        val viewPager2: ViewPager2 = rootView.findViewById(R.id.statistics_viewpager)
        viewPager2.adapter = ViewPager2AdapterTabListSubtabs(this)
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
        })
        val tlm = TabLayoutMediator(subTabs, viewPager2) { tab, position ->
            tab.text = if (position == 0) getString(R.string.general) else Difficulty.getLocalString(position - 1, resources)
        }
        tlm.attach()
    }
}

class ViewPager2AdapterTabListSubtabs(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun createFragment(position: Int): Fragment {
        return TabStatisticsSubtab.newInstance(position - 1)
    }

    override fun getItemCount(): Int {
        return Difficulty.values().size + 1
    }
}