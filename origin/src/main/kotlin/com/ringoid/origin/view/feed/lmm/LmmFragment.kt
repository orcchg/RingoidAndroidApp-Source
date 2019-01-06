package com.ringoid.origin.view.feed.lmm

import com.ringoid.base.view.BaseFragment
import com.ringoid.origin.R

class LmmFragment : BaseFragment<LmmViewModel>() {

    companion object {
        fun newInstance(): LmmFragment = LmmFragment()
    }

    override fun getVmClass(): Class<LmmViewModel> = LmmViewModel::class.java

    override fun getLayoutId(): Int = R.layout.fragment_lmm
}
