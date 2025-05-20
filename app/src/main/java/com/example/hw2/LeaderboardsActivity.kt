package com.example.hw2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class LeaderboardsActivity : AppCompatActivity(),
    OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboards)

        // 1) ViewPager + Tabs
        val pager = findViewById<ViewPager2>(R.id.view_pager)
        pager.adapter = LeaderboardPagerAdapter(this)
        TabLayoutMediator(
            findViewById<TabLayout>(R.id.tab_layout),
            pager
        ) { tab, pos ->
            tab.text = when(pos) {
                0 -> "Buttons Slow"
                1 -> "Buttons Fast"
                else -> "Sensor"
            }
        }.attach()

        // 2) Obtain the SupportMapFragment and request the map
        val mapFrag = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFrag.getMapAsync(this)
    }

    // Called when the map is ready to use
    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Default camera to Israel
        val israel = LatLng(31.0461, 34.8516)
        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(israel, 6f)
        )
    }

    /** Called by each TableFragment when a score is tapped */
    fun showOnMap(lat: Double, lon: Double, label: String) {
        // clear old markers, add new one, animate in
        googleMap.clear()
        val pos = LatLng(lat, lon)
        googleMap.addMarker(
            MarkerOptions()
                .position(pos)
                .title(label)
        )
        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(pos, 15f)
        )
    }
}
