package com.ringoid.origin.imagepreview.view

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.jakewharton.rxbinding3.view.clicks
import com.ringoid.base.view.BaseFragment
import com.ringoid.origin.GlideApp
import com.ringoid.origin.imagepreview.R
import com.ringoid.origin.navigation.NavigateFrom
import com.ringoid.origin.navigation.navigateAndClose
import com.ringoid.utility.clickDebounce
import com.ringoid.utility.randomString
import com.steelkiwi.cropiwa.config.CropIwaSaveConfig
import kotlinx.android.synthetic.main.fragment_image_preview.*
import timber.log.Timber
import java.io.File

class ImagePreviewFragment : BaseFragment<ImagePreviewViewModel>() {

    companion object {
        const val TAG = "ImagePreviewFragment_tag"

        private const val BUNDLE_KEY_IMAGE_URI = "bundle_key_image_uri"
        private const val BUNDLE_KEY_NAVIGATE_FROM = "bundle_key_navigate_from"

        fun newInstance(uri: Uri?, navigateFrom: String): ImagePreviewFragment =
            ImagePreviewFragment().apply {
                arguments = Bundle().apply {
                    Timber.v("ImagePreview: uri=$uri, navigateFrom=$navigateFrom")
                    putParcelable(BUNDLE_KEY_IMAGE_URI, uri)
                    putString(BUNDLE_KEY_NAVIGATE_FROM, navigateFrom)
                }
            }
    }

    protected var uri: Uri? = null

    override fun getVmClass(): Class<ImagePreviewViewModel> = ImagePreviewViewModel::class.java

    override fun getLayoutId(): Int = R.layout.fragment_image_preview

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = arguments?.getParcelable<Uri>(BUNDLE_KEY_IMAGE_URI)?.also {
            activity?.contentResolver?.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Suppress("CheckResult", "AutoDispose")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_done.clicks().compose(clickDebounce()).subscribe { cropImage() }

        uri?.let { crop_view.setImageUri(it) }
           ?: run {
               Timber.w("No image uri supplied on ImagePreview screen.")
               arguments?.getString(BUNDLE_KEY_NAVIGATE_FROM)
                             ?.takeIf { it == NavigateFrom.SCREEN_LOGIN }
                             ?.let { navigateAndClose(this, path = "/main?tab=${NavigateFrom.MAIN_TAB_PROFILE}") }
           }
    }

    override fun onDestroyView() {
        crop_view.setCropSaveCompleteListener(null)
        super.onDestroyView()
    }

    // --------------------------------------------------------------------------------------------
    private fun cropImage() {
        context?.let {
            val context = it
            val destinationFile = File(it.filesDir, "${randomString()}.png")
            crop_view.let {
                val imageConfig = CropIwaSaveConfig.Builder(Uri.fromFile(destinationFile))
                    .setCompressFormat(Bitmap.CompressFormat.PNG)
                    .setQuality(100) // hint for lossy compression formats
                    .build()
                it.setCropSaveCompleteListener { GlideApp.with(context).load(destinationFile).preload() }
                it.crop(imageConfig)
            }
        }
        activity?.onBackPressed()  // close this ImagePreview screen immediately, doing cropping in background
    }
}
