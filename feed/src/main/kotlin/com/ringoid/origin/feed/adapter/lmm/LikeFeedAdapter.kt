package com.ringoid.origin.feed.adapter.lmm

import android.view.View
import com.ringoid.origin.feed.model.ProfileImageVO

class LikeFeedAdapter : BaseLmmAdapter() {

    var onLikeImageListener: ((model: ProfileImageVO, position: Int) -> Unit)? = null

    override fun instantiateViewHolder(view: View): LmmViewHolder =
        super.instantiateViewHolder(view).also { vh ->
            vh.profileImageAdapter.itemClickListener = onLikeImageListener
        }
}
// TODO: show message button only after like tap