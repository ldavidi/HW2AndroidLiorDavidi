package com.example.hw2

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LeaderboardPagerAdapter(fa: FragmentActivity)
    : FragmentStateAdapter(fa) {

    private val versions = listOf("buttons_slow","buttons_fast","sensors")

    override fun getItemCount() = versions.size

    override fun createFragment(pos: Int): Fragment =
        TableFragment.newInstance(versions[pos])
}
