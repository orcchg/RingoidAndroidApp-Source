package com.ringoid.origin.view.adapter.feed

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ringoid.base.adapter.BaseViewHolder
import com.ringoid.domain.model.feed.Profile
import kotlinx.android.synthetic.main.rv_item_feed_profile.view.*

class ProfileViewHolder(view: View, viewPool: RecyclerView.RecycledViewPool? = null)
    : BaseViewHolder<Profile>(view) {

    private val profileImageAdapter = ProfileImageAdapter()

    init {
        itemView.rv_items.apply {
            adapter = profileImageAdapter
            isNestedScrollingEnabled = false
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            setHasFixedSize(true)
            setRecycledViewPool(viewPool)
            setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
        }
    }

    override fun bind(model: Profile) {
        profileImageAdapter.submitList(model.images)
    }
}
