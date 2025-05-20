package com.example.hw2

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.*

class ScoreAdapter(
    private val onClick: (Score)->Unit
) : ListAdapter<Score, ScoreAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Score>() {
        override fun areItemsTheSame(a: Score, b: Score) = a === b
        override fun areContentsTheSame(a: Score, b: Score) = a == b
    }

    inner class VH(item: View) : RecyclerView.ViewHolder(item) {
        private val text = item.findViewById<TextView>(android.R.id.text1)
        fun bind(s: Score) {
            text.text = "Score: ${s.value}  (c:${s.coins}  d:${s.seconds})"
            itemView.setOnClickListener { onClick(s) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context)
            .inflate(android.R.layout.simple_list_item_1, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
