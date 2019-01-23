package com.ringoid.origin.feed.view.lmm.like

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import com.ringoid.base.observe
import com.ringoid.domain.model.feed.FeedItem
import com.ringoid.origin.feed.adapter.base.OriginFeedViewHolder
import com.ringoid.origin.feed.adapter.lmm.like.BaseLikeFeedAdapter
import com.ringoid.origin.feed.view.lmm.ILmmFragment
import com.ringoid.origin.feed.view.lmm.base.BaseLmmFeedFragment
import com.ringoid.origin.feed.view.lmm.base.BaseLmmFeedViewModel
import com.ringoid.origin.view.main.IMainActivity
import com.ringoid.utility.communicator
import kotlinx.android.synthetic.main.fragment_feed.*

abstract class BaseLikesFeedFragment<VM : BaseLmmFeedViewModel, VH : OriginFeedViewHolder<FeedItem>>
    : BaseLmmFeedFragment<VM, VH>() {

    abstract fun instantiateFeedAdapter(): BaseLikeFeedAdapter<VH>

    override fun createFeedAdapter(): BaseLikeFeedAdapter<VH> =
        instantiateFeedAdapter().apply {
            messageClickListener = { model: FeedItem, position: Int ->
                (rv_items.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(position, 0)
                communicator(IMainActivity::class.java)?.openChat(peerId = model.id)
            }
        }

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        /**
         * Parent's [Fragment.onActivityCreated] is called before this method on any child [Fragment],
         * so it's safe to access parent's [ViewModel] here, because it's already initialized.
         */
        communicator(ILmmFragment::class.java)?.accessViewModel()
            ?.let { parentVm -> viewLifecycleOwner.observe(parentVm.listScrolls, ::scrollListToPosition) }
    }
}
