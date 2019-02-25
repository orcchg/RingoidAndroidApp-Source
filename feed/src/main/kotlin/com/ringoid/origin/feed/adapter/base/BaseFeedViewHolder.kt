package com.ringoid.origin.feed.adapter.base

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.ringoid.base.adapter.BaseViewHolder
import com.ringoid.domain.model.feed.IProfile
import com.ringoid.domain.model.feed.Profile
import com.ringoid.origin.feed.adapter.profile.ProfileImageAdapter
import com.ringoid.origin.feed.adapter.profile.ProfileImageItemAnimator
import com.ringoid.origin.feed.model.ProfileImageVO
import com.ringoid.origin.view.common.visibility_tracker.TrackingBus
import com.ringoid.utility.changeVisibility
import com.ringoid.utility.collection.EqualRange
import com.ringoid.utility.linearLayoutManager
import com.ringoid.widget.view.rv.EnhancedPagerSnapHelper
import kotlinx.android.synthetic.main.rv_item_feed_profile_content.view.*
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper
import timber.log.Timber

interface IFeedViewHolder {

    var trackingBus: TrackingBus<EqualRange<ProfileImageVO>>?

    fun getCurrentImagePosition(): Int
}

abstract class OriginFeedViewHolder<T : IProfile>(view: View, viewPool: RecyclerView.RecycledViewPool? = null)
    : BaseViewHolder<T>(view), IFeedViewHolder {

    override var trackingBus: TrackingBus<EqualRange<ProfileImageVO>>? = null

    override fun getCurrentImagePosition(): Int = 0
}

abstract class BaseFeedViewHolder<T : IProfile>(view: View, viewPool: RecyclerView.RecycledViewPool? = null)
    : OriginFeedViewHolder<T>(view, viewPool) {

    internal val profileImageAdapter = ProfileImageAdapter(view.context)
    override var trackingBus: TrackingBus<EqualRange<ProfileImageVO>>? = null

    private val imagePreloadListener: RecyclerViewPreloader<ProfileImageVO>

    init {
        val snapHelper = EnhancedPagerSnapHelper(duration = 30)
        itemView.rv_items.apply {
            adapter = profileImageAdapter.also { it.tabsObserver = itemView.tabs.adapterDataObserver }
            isNestedScrollingEnabled = false
            itemAnimator = ProfileImageItemAnimator()
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                .also { it.initialPrefetchItemCount = 4 }
            snapHelper.attachToRecyclerView(this)
            itemView.tabs.attachToRecyclerView(this, snapHelper)
            setHasFixedSize(true)
            setRecycledViewPool(viewPool)
            setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
            OverScrollDecoratorHelper.setUpOverScroll(this, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    rv.linearLayoutManager()?.let {
                        val from = it.findFirstVisibleItemPosition()
                        val to = it.findLastVisibleItemPosition()
                        val items = profileImageAdapter.getItemsExposed(from = from, to = to)
                        Timber.v("Visible profile images [${items.size}] [$from, $to]: $items")
                        trackingBus?.postViewEvent(EqualRange(from = from, to = to, items = items))
                    }
                }
            })
            imagePreloadListener = RecyclerViewPreloader(Glide.with(this), profileImageAdapter, ViewPreloadSizeProvider<ProfileImageVO>(), 10)
            addOnScrollListener(imagePreloadListener)
        }
    }

    override fun bind(model: T) {
        showControls()  // cancel any effect caused by applied payloads
        profileImageAdapter.apply {
            clear()  // clear old items, preventing animator to animate change upon async diff calc finishes
            submitList(model.images.map { ProfileImageVO(profileId = model.id, image = it) })
        }
    }

    override fun bind(model: T, payloads: List<Any>) {
        if (payloads.contains(FeedViewHolderHideControls)) {
            hideControls()
        }
        if (payloads.contains(FeedViewHolderShowControls)) {
            showControls()
        }

        // scroll affected
        if (payloads.contains(FeedViewHolderHideSettingsBtnOnScroll)) {
            itemView.ibtn_settings.changeVisibility(isVisible = false)
        }
        if (payloads.contains(FeedViewHolderShowSettingsBtnOnScroll)) {
            itemView.ibtn_settings.changeVisibility(isVisible = true)
        }
        if (payloads.contains(FeedViewHolderHideTabsIndicatorOnScroll)) {
            itemView.tabs.changeVisibility(isVisible = false)
        }
        if (payloads.contains(FeedViewHolderShowSettingsBtnOnScroll)) {
            itemView.tabs.changeVisibility(isVisible = true)
        }
        if (payloads.contains(FeedViewHolderHideLikeBtnOnScroll)) {
            profileImageAdapter.notifyItemChanged(getCurrentImagePosition(), FeedViewHolderHideControls)
        }
        if (payloads.contains(FeedViewHolderShowLikeBtnOnScroll)) {
            profileImageAdapter.notifyItemChanged(getCurrentImagePosition(), FeedViewHolderShowControls)
        }
    }

    // ------------------------------------------------------------------------
    protected open fun hideControls() {
        itemView.apply {
            tabs.changeVisibility(isVisible = false)
            ibtn_settings.changeVisibility(isVisible = false)
        }
        profileImageAdapter.notifyItemChanged(getCurrentImagePosition(), FeedViewHolderHideControls)
    }

    protected open fun showControls() {
        itemView.apply {
            tabs.changeVisibility(isVisible = true)
            ibtn_settings.changeVisibility(isVisible = true)
        }
        profileImageAdapter.notifyItemChanged(getCurrentImagePosition(), FeedViewHolderShowControls)
    }

    override fun getCurrentImagePosition(): Int =
        itemView.rv_items.linearLayoutManager()?.findFirstVisibleItemPosition() ?: 0
}

class FeedViewHolder(view: View, viewPool: RecyclerView.RecycledViewPool? = null)
    : BaseFeedViewHolder<Profile>(view, viewPool)

class HeaderFeedViewHolder(view: View) : OriginFeedViewHolder<Profile>(view), IFeedViewHolder {

    override fun bind(model: Profile) {
        // no-op
    }
}
