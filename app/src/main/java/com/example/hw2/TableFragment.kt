package com.example.hw2

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import kotlinx.coroutines.*

class TableFragment : Fragment(R.layout.fragment_table) {

    companion object {
        private const val ARG_VER = "version"
        fun newInstance(version: String) =
            TableFragment().apply {
                arguments = Bundle().apply { putString(ARG_VER, version) }
            }
    }

    private lateinit var version: String

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        version = requireArguments().getString(ARG_VER)!!
    }

    override fun onViewCreated(v: View, saved: Bundle?) {
        val rv = v.findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ScoreAdapter { score ->
            // pass back to Activity to update map
            (activity as LeaderboardsActivity)
                .showOnMap(score.lat, score.lon, "Score: ${score.value}")
        }
        rv.adapter = adapter

        // load from our in-memory store
        adapter.submitList(ScoreManager.topScores(version))
    }
}
