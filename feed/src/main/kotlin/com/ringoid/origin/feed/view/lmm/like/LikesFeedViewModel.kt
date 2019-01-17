package com.ringoid.origin.feed.view.lmm.like

import android.app.Application
import com.ringoid.origin.feed.view.FeedViewModel
import javax.inject.Inject

class LikesFeedViewModel @Inject constructor(app: Application) : FeedViewModel(app) {

    override fun getFeed() {
        // TODO: get only likes part of Lmm here
    }

    override fun getFeedName(): String = "who_liked_me"
}