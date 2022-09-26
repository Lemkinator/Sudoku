package de.lemke.sudoku.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import kotlinx.coroutines.launch

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
            tab.text = resources.getStringArray(R.array.difficuilty)[position]
        }
        tlm.attach()
    }
}

class ViewPager2AdapterTabListSubtabs(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun createFragment(position: Int): Fragment {
        return TabStatisticsSubtab.newInstance(position)
    }

    override fun getItemCount(): Int {
        return Difficulty.values().size
    }
}


@AndroidEntryPoint
class TabStatisticsSubtab : Fragment() {
    private lateinit var rootView: View
    private lateinit var subTabs: MarginsTabLayout
    private lateinit var mainTabs: MarginsTabLayout
    private lateinit var viewPager2List: ViewPager2
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private var difficulty: Difficulty = Difficulty.EASY

    companion object {
        fun newInstance(position: Int): TabStatisticsSubtab {
            val f = TabStatisticsSubtab()
            val args = Bundle()
            args.putInt("position", position)
            f.arguments = args
            return f
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_tab_statistics_subtab, container, false)
        difficulty = Difficulty.fromInt(arguments?.getInt("position") ?: 0)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        subTabs = activity.findViewById(R.id.fragment_statistics_sub_tabs)
        mainTabs = activity.findViewById(R.id.main_tabs)
        viewPager2List = activity.findViewById(R.id.statistics_viewpager)
        lifecycleScope.launch {

        }

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {

            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }
}