package com.ringoid.origin.profile.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.clicks
import com.ringoid.base.IBaseRingoidApplication
import com.ringoid.base.IImagePreviewReceiver
import com.ringoid.base.observe
import com.ringoid.base.observeOneShot
import com.ringoid.base.view.BaseFragment
import com.ringoid.base.view.ViewState
import com.ringoid.debug.DebugLogUtil
import com.ringoid.domain.BuildConfig
import com.ringoid.domain.DomainUtil
import com.ringoid.domain.Onboarding
import com.ringoid.domain.misc.Gender
import com.ringoid.domain.misc.UserProfileEditablePropertyId
import com.ringoid.domain.misc.UserProfilePropertyId
import com.ringoid.domain.model.image.IImage
import com.ringoid.origin.AppRes
import com.ringoid.origin.error.handleOnView
import com.ringoid.origin.model.UserProfileProperties
import com.ringoid.origin.navigation.*
import com.ringoid.origin.profile.OriginR_string
import com.ringoid.origin.profile.R
import com.ringoid.origin.profile.WidgetR_attrs
import com.ringoid.origin.profile.WidgetR_color
import com.ringoid.origin.profile.adapter.UserProfileImageAdapter
import com.ringoid.origin.view.common.EmptyFragment
import com.ringoid.origin.view.common.IEmptyScreenCallback
import com.ringoid.origin.view.dialog.Dialogs
import com.ringoid.origin.view.main.IBaseMainActivity
import com.ringoid.origin.view.particles.PARTICLE_TYPE_LIKE
import com.ringoid.origin.view.particles.PARTICLE_TYPE_MATCH
import com.ringoid.origin.view.particles.PARTICLE_TYPE_MESSAGE
import com.ringoid.utility.*
import com.ringoid.widget.view.rv.EnhancedPagerSnapHelper
import com.ringoid.widget.view.swipes
import kotlinx.android.synthetic.main.fragment_profile.*
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper
import timber.log.Timber
import java.util.*

class UserProfileFragment : BaseFragment<UserProfileFragmentViewModel>(), IEmptyScreenCallback {

    companion object {
        fun newInstance(): UserProfileFragment = UserProfileFragment()
    }

    private val calendar = Calendar.getInstance()

    private var imageOnViewPortId: String = DomainUtil.BAD_ID
    private var currentImagePosition: Int = 0
    private var cropImageAfterLogin: Boolean = false  // for Onboarding.ADD_IMAGE
    private var handleRequestToAddImage: Boolean = false
    private var handleRequestToCheckNoImagesAndAddImage: Boolean = false

    private lateinit var imagesAdapter: UserProfileImageAdapter
    private val pageSelectListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            recyclerView.linearLayoutManager()?.findFirstCompletelyVisibleItemPosition()
                ?.takeIf { it != RecyclerView.NO_POSITION }
                ?.takeIf { it != currentImagePosition }  // skip excess scrolling w.o changing position
                ?.let { position -> onImageSelect(position) }
        }
    }

    private var withAbout: Boolean = false  // 'about' property is not empty
    private var withLabel: Boolean = false  // profile has at least one property (excluding 'about')
    @DebugOnly
    private var debugAddImage: Boolean = false

    override fun getVmClass(): Class<UserProfileFragmentViewModel> = UserProfileFragmentViewModel::class.java

    override fun getLayoutId(): Int = R.layout.fragment_profile

    // --------------------------------------------------------------------------------------------
    override fun onViewStateChange(newState: ViewState) {
        fun onIdleState() {
            pb_profile.changeVisibility(isVisible = false)
            swipe_refresh_layout.isRefreshing = false
        }
        fun onLoadingState() {
            pb_profile.changeVisibility(isVisible = BuildConfig.IS_STAGING)
        }

        super.onViewStateChange(newState)
        when (newState) {
            is ViewState.CLEAR -> {
                onIdleState()
                when (newState.mode) {
                    ViewState.CLEAR.MODE_EMPTY_DATA -> showNoImageStub(true)
                    ViewState.CLEAR.MODE_NEED_REFRESH -> showErrorStub()
                    ViewState.CLEAR.MODE_CHANGE_FILTERS -> showNoImageStub(true)
                }
            }
            is ViewState.IDLE -> onIdleState()
            is ViewState.LOADING -> onLoadingState()
            is ViewState.ERROR -> newState.e.handleOnView(this, ::onIdleState) { onRefresh() }
            else -> { /* no-op */ }
        }
    }

    // ------------------------------------------
    override fun onTabTransaction(payload: String?) {
        super.onTabTransaction(payload)
        payload?.let {
            when (it) {
                Payload.PAYLOAD_PROFILE_CHECK_NO_IMAGES_AND_REQUEST_ADD_IMAGE -> {
                    if (isAdded && isViewModelInitialized) {
                        vm.countUserImages()
                    } else {
                        handleRequestToCheckNoImagesAndAddImage = true
                    }
                }
                Payload.PAYLOAD_PROFILE_LOGIN_IMAGE_ADDED -> { cropImageAfterLogin = true }  // for Onboarding.ADD_IMAGE
                Payload.PAYLOAD_PROFILE_REQUEST_ADD_IMAGE -> {
                    if (isAdded) {
                        onAddImage()
                    } else {
                        /**
                         * Postpone call [onAddImage] until all necessary fields initialized.
                         */
                        handleRequestToAddImage = true
                    }
                }
            }
        }
    }

    /* Lifecycle */
    // --------------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagesAdapter = UserProfileImageAdapter()
            .apply {
                onInsertListener = { count ->
                    DebugLogUtil.v("Inserting $count images on User Profile")
                    showNoImageStub(needShow = count <= 0)
                    if (count > 1) {  // inserted multiple items
                        // restore position at image on viewport
                        imagesAdapter.getModelAdapterPosition { it.id == imageOnViewPortId }
                            .takeIf { it != DomainUtil.BAD_POSITION }
                            ?.let { scrollToPosition(it) }
                            ?: run { scrollToPosition(0) }  // no previously seen image - scroll to the first image
                    } else if (count > 0) {  // inserted single item
                        scrollToPosition(0)  // for append: imagesAdapter.itemCount - 1
                    }
                    showDotTabs(isVisible = true)
                }
                onRemoveListener = {
                    val empty = imagesAdapter.isEmpty()
                    DebugLogUtil.v("Removed images on User Profile, is empty: $empty")
                    showNoImageStub(needShow = empty)
                    showDotTabs(isVisible = true)
                }
                itemClickListener = { _, _ -> navigate(this@UserProfileFragment, path = "/settings_profile") }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ExternalNavigator.RC_GALLERY_GET_IMAGE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> navigate(this, path = "/imagepreview", rc = RC_IMAGE_PREVIEW, payload = data)
                }
            }
            RequestCode.RC_DELETE_IMAGE_DIALOG -> {
                showControls(isVisible = true)
                if (resultCode == Activity.RESULT_OK) {
                    if (data == null) {
                        val e = NullPointerException("No output image id from Delete Image dialog - this is an error!")
                        Timber.e(e) ; throw e
                    }

                    if (data.hasExtra("debug")) {  // DebugOnly
                        vm.deleteImageDebug(id = data.getStringExtra("imageId"))
                    } else {
                        vm.deleteImage(id = data.getStringExtra("imageId"))
                    }
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        /**
         * Asks to add first photo to user profile.
         */
        fun askForImage(count: Int) {
            Dialogs.showTextDialog(activity,
                descriptionResId = OriginR_string.profile_empty_images_dialog,
                positiveBtnLabelResId = OriginR_string.button_add_photo,
                negativeBtnLabelResId = OriginR_string.button_later,
                positiveListener = { _, _ -> onAddImage() },
                isCancellable = false)
        }

        /**
         * Asks to add another photo to user profile.
         */
        fun askForAnotherImage() {
            Dialogs.showTextDialog(activity,
                titleResId = OriginR_string.profile_dialog_image_another_common_title, descriptionResId = 0,
                positiveBtnLabelResId = OriginR_string.button_add_photo,
                negativeBtnLabelResId = OriginR_string.button_later,
                positiveListener = { _, _ -> onAddImage() })
        }

        /**
         * Asks to add another photo to user profile or navigate on Explore feed screen instead.
         *
         * @note: for Onboarding.ADD_IMAGE.
         */
        fun askForAnotherImageAfterLogin() {
            Dialogs.showTextDialog(activity,
                titleResId = OriginR_string.profile_dialog_image_another_title, descriptionResId = 0,
                positiveBtnLabelResId = OriginR_string.profile_dialog_image_another_button_add,
                negativeBtnLabelResId = OriginR_string.profile_dialog_image_another_button_cancel,
                positiveListener = { _, _ -> cropImageAfterLogin = true ; onAddImage() },
                negativeListener = { _, _ -> navigate(this@UserProfileFragment, path = "/main?tab=${NavigateFrom.MAIN_TAB_EXPLORE}") })
        }

        fun onCropFailed(e: Throwable) {
            Timber.e(e, "Image crop has failed")
            Dialogs.supportDialog(this@UserProfileFragment, OriginR_string.error_crop_image)
            debugAddImage = false

            if (cropImageAfterLogin) {  // for Onboarding.ADD_IMAGE
                // on crop error after login
                cropImageAfterLogin = false  // ask only once per session
                showNoImageStub(needShow = true)
            }
        }

        fun onCropSuccess(croppedUri: Uri) {
            Timber.v("Image cropping has succeeded, uri: $croppedUri")
            if (debugAddImage) {
                vm.uploadImageDebug(uri = croppedUri)
            } else {
                vm.uploadImage(uri = croppedUri)
            }
            debugAddImage = false

            if (cropImageAfterLogin) {  // for Onboarding.ADD_IMAGE
                // on crop success after login
                cropImageAfterLogin = false  // ask only once per session
                askForAnotherImageAfterLogin()
            } else {
                askForAnotherImage()
            }
        }

        fun addLabelView(
                containerView: ViewGroup,
                gender: Gender,
                propertyId: UserProfilePropertyId,
                properties: UserProfileProperties,
                useDefault: Boolean) {
            UserProfileScreenUtils.createLabelView(
                container = containerView,
                gender = gender,
                propertyId = propertyId,
                properties = properties,
                useDefault = useDefault)
            ?.let { labelView ->
                containerView.addView(labelView)
                labelView.changeVisibility(isVisible = false)
            }
        }

        fun onProfilePropertiesUpdate(properties: UserProfileProperties) {
            val age = maxOf(0, calendar.get(Calendar.YEAR) - spm.currentUserYearOfBirth())
            val gender = spm.currentUserGender()
            val showDefault = properties.isAllUnknown()

            withLabel = UserProfileScreenUtils.hasAtLeastOneProperty(properties)
            properties.about().let { about ->
                withAbout = about.isNotBlank()
                tv_about.text = about.trim()
            }
            properties.status().let { status ->
                tv_status.text = status.trim()
            }

            mutableListOf<String>().apply {
                properties.name()
                    .takeIf { it.isNotBlank() }
                    ?.let { name -> add(name) }
                    ?: run { add(resources.getString(OriginR_string.settings_profile_item_custom_property_name)) }

                if (spm.hasUserYearOfBirth()) {
                    age.takeIf { it >= 18 }?.let { age -> add("$age") }
                }
            }
            .let { tv_name_age.text = it.joinToString() }

            ll_left_section?.let { containerView ->
                containerView.removeAllViews()
                when (gender) {
                    Gender.FEMALE -> UserProfileScreenUtils.propertiesFemale
                    else -> UserProfileScreenUtils.propertiesMale
                }
                .forEach { propertyId -> addLabelView(containerView, gender, propertyId, properties, showDefault) }
            }
            ll_right_section?.let { containerView ->
                containerView.removeAllViews()
                UserProfileScreenUtils.propertiesRight
                    .forEach { propertyId -> addLabelView(containerView, gender, propertyId, properties, showDefault) }
            }
            onImageSelect(position = currentImagePosition)
        }

        // --------------------------------------
        super.onActivityCreated(savedInstanceState)
        with(viewLifecycleOwner) {
            observe(vm.imageBlocked(), ::doOnBlockedImage)
            observe(vm.imageCreated(), imagesAdapter::prepend)
            observe(vm.imageDeleted(), imagesAdapter::remove)
            observe(vm.images(), imagesAdapter::submitList)
            observe(vm.profile(), ::onProfilePropertiesUpdate)
            observeOneShot(vm.requestToAddImageOneShot(), ::askForImage)
        }

        showBeginStub()  // empty stub will be replaced after adapter's filled
        if (!cropImageAfterLogin) {
            showNoImageStub(needShow = true)
        }

        /**
         * Register listener on success or failure of image cropping.
         *
         * For [Onboarding.ADD_IMAGE] mode:
         *
         * If image cropping has finished before this place (i.e. prior to Profile screen's created),
         * get that image from the global in-memory cache and show on Profile screen.
         * The image will be immediately passed into [onCropSuccess] callback upon subscribed
         * (at the moment of call [IImagePreviewReceiver.subscribe]).
         */
        globalImagePreviewReceiver()
            ?.doOnError(::onCropFailed)
            ?.doOnSuccess(::onCropSuccess)

        /**
         * Initialization logic: when new user has just logged in, if he (she) has uploaded any image
         * during sign up process, then get that last prepared image, otherwise show empty stub.
         * When user has already logged in and then restarts the app, if it had not been started yet,
         * the app does refresh on Profile screen.
         */
        if (communicator(IBaseMainActivity::class.java)?.isNewUser() == true) {
            /**
             * For [Onboarding.ADD_IMAGE] mode:
             *
             * If image has not been prepared yet after cropping, but Profile screen had already
             * been created, so we are actually at this place, subscribe to get image once it's ready.
             * The image will be passed into [onCropSuccess] callback.
             *
             * If image has already been prepared before this place, call [IImagePreviewReceiver.subscribe]
             * will immediately pass it into [onCropSuccess] callback.
             */
            globalImagePreviewReceiver()?.subscribe()  // get last prepared image, if any
        } else {
            // refresh Profile screen for already logged in user on a fresh app's start
            onRefresh()
        }

        if (handleRequestToAddImage) {  // postponed handling to ensure initialization
            handleRequestToAddImage = false  // don't reuse flag
            // redirect from other screen (in-app navigation with PAYLOAD_PROFILE_REQUEST_ADD_IMAGE payload)
            onAddImage()
        }
        if (handleRequestToCheckNoImagesAndAddImage) {
            handleRequestToCheckNoImagesAndAddImage = false
            vm.countUserImages()
        }
    }

    @Suppress("CheckResult", "AutoDispose")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adjustViews()
        ibtn_add_image.clicks().compose(clickDebounce())
            .subscribe {
                if (!connectionManager.isNetworkAvailable()) {
                    noConnection(this)
                    return@subscribe
                }
                onAddImage()
            }
        with (ibtn_add_image_debug) {
            changeVisibility(isVisible = BuildConfig.DEBUG)
            clicks().compose(clickDebounce()).subscribe {
                debugAddImage = true
                onAddImage()
            }
        }
        ibtn_delete_image.clicks().compose(clickDebounce()).subscribe {
            if (!connectionManager.isNetworkAvailable()) {
                noConnection(this)
                return@subscribe
            }

            imageOnViewPort()?.let { image ->
                showControls(isVisible = false)
                navigate(this, path = "/delete_image?imageId=${image.id}", rc = RequestCode.RC_DELETE_IMAGE_DIALOG)
            }
        }
        ibtn_profile_edit.clicks().compose(clickDebounce()).subscribe { navigate(this, path = "/settings_profile") }
        ibtn_settings.clicks().compose(clickDebounce()).subscribe { navigate(this, path = "/settings") }
        swipe_refresh_layout.apply {
//            setColorSchemeResources(*resources.getIntArray(R.array.swipe_refresh_colors))
            refreshes().compose(clickDebounce()).subscribe { onRefresh() }
            swipes().compose(clickDebounce()).subscribe { vm.onStartRefresh() }
        }
        val snapHelper = EnhancedPagerSnapHelper(duration = 30)
        rv_items.apply {
            adapter = imagesAdapter.also { it.tabsObserver = tabs2.adapterDataObserver }
            isNestedScrollingEnabled = false
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            snapHelper.attachToRecyclerView(this)
            tabs2.attachToRecyclerView(this, snapHelper)
            setHasFixedSize(true)
            setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
            OverScrollDecoratorHelper.setUpOverScroll(this, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL)
            addOnScrollListener(pageSelectListener)
        }
        tv_app_title.clicks().compose(clickDebounce()).subscribe { navigate(this, path = "/settings") }
        tv_status.clicks().compose(clickDebounce()).subscribe { navigate(this, path = "/settings_profile?focus=${UserProfileEditablePropertyId.STATUS}") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        with (rv_items) {
            removeOnScrollListener(pageSelectListener)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    this@with.adapter = null
                }
            })
            adapter = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        globalImagePreviewReceiver()?.dispose()
        imagesAdapter.dispose()
    }

    // --------------------------------------------------------------------------------------------
    private fun onRefresh() {
        if (!connectionManager.isNetworkAvailable()) {
            swipe_refresh_layout.isRefreshing = false
            noConnection(this@UserProfileFragment)
        } else {
            imageOnViewPortId = imageOnViewPort()?.id ?: DomainUtil.BAD_ID
            vm.refresh()
        }
    }

    // ------------------------------------------
    private fun onAddImage() {
        ExternalNavigator.openGalleryToGetImageFragment(this)
    }

    private fun doOnBlockedImage(imageId: String) {
        Dialogs.showTextDialog(activity,
            titleResId = OriginR_string.profile_dialog_image_blocked_title, descriptionResId = 0,
            positiveBtnLabelResId = OriginR_string.profile_dialog_image_blocked_button,
            negativeBtnLabelResId = OriginR_string.button_close,
            positiveListener = { _, _ -> navigate(this, path = "/webpage?url=${AppRes.WEB_URL_TERMS}") })
    }

    private fun onImageSelect(position: Int) {
        fun showLabels(containerView: ViewGroup, startIndex: Int, endIndex: Int) {
            with (containerView) {
                for (i in 0 until childCount) {
                    getChildAt(i).changeVisibility(isVisible = false)
                }
                if (startIndex < childCount) {
                    changeVisibility(isVisible = !fl_empty_container.isVisible())
                    for (i in startIndex until minOf(endIndex, childCount)) {
                        getChildAt(i).changeVisibility(isVisible = true)
                    }
                } else {
                    changeVisibility(isVisible = false)
                }
            }
        }

        fun showAbout() {
            tv_about.changeVisibility(isVisible = !fl_empty_container.isVisible())
            ll_left_section.changeVisibility(isVisible = false)
            ll_right_section.changeVisibility(isVisible = false)
        }

        fun showLabels(startIndex: Int, endIndex: Int) {
            tv_about.changeVisibility(isVisible = false)
            showLabels(ll_left_section, startIndex, endIndex)
            showLabels(ll_right_section, startIndex, endIndex)
        }

        // --------------------------------------
        imageOnViewPortId = imagesAdapter.findModel(position)?.id ?: DomainUtil.BAD_ID
        currentImagePosition = position
        val page = if (withAbout) maxOf(0, position - 1) else position
        val startIndex = page * UserProfileScreenUtils.COUNT_LABELS_ON_PAGE
        val endIndex = startIndex + UserProfileScreenUtils.COUNT_LABELS_ON_PAGE

        if (isAboutPage(position)) {
            showAbout()  // show 'about' on predefined position, if any
        } else {
            showLabels(startIndex, endIndex)
        }
    }

    private fun isAboutPage(page: Int): Boolean {
        /**
         * Calculates page on which 'about' property should be displayed.
         * @note: This method must be called inside 'if (withAbout)' block.
         */
        fun positionForAboutIfPresent(): Int = if (withLabel) 1 else 0

        return if (withAbout) page == positionForAboutIfPresent() else false
    }

    private fun isAboutVisible(): Boolean = isAboutPage(page = currentImagePosition)

    // ------------------------------------------
    private fun showBeginStub() {  // empty stub without labels, plain clean stub
        showEmptyStub(needShow = true, input = EmptyFragment.Companion.Input())
    }

    private fun showNoImageStub(needShow: Boolean) {  // empty stub with label 'Add photo to receive likes'
        showEmptyStub(needShow, input =
            EmptyFragment.Companion.Input(
                emptyTextResId = OriginR_string.profile_empty_images,
                labelTextColor = context?.getAttributeColor(WidgetR_attrs.refTextColorPrimary) ?: ContextCompat.getColor(context!!, WidgetR_color.primary_text),
                isLabelClickable = true))
        communicator(IBaseMainActivity::class.java)?.showBadgeWarningOnProfile(isVisible = needShow)
    }

    private fun showErrorStub() {  // empty stub with label 'Pull to refresh'
        showEmptyStub(needShow = true, input = EmptyFragment.Companion.Input(emptyTextResId = OriginR_string.common_pull_to_refresh))
    }

    private fun showEmptyStub(needShow: Boolean, input: EmptyFragment.Companion.Input) {
        fl_empty_container.changeVisibility(isVisible = needShow)
        gradient.changeVisibility(isVisible = !needShow)
        showImageControls(isVisible = !needShow)
        if (!needShow) {
            return
        }

        input.apply {
            val emptyFragment = EmptyFragment.newInstance(this)
            childFragmentManager
                .beginTransaction()
                .replace(R.id.fl_empty_container, emptyFragment, EmptyFragment.TAG)
                .commitNowAllowingStateLoss()
        }
    }

    override fun onEmptyLabelClick() {
        // click on empty screen label should open Gallery to choose image
        onAddImage()
    }

    // ------------------------------------------
    private fun globalImagePreviewReceiver(): IImagePreviewReceiver? =
        this@UserProfileFragment.communicator(IBaseRingoidApplication::class.java)?.imagePreviewReceiver

    private fun imageOnViewPort(): IImage? =
        rv_items?.linearLayoutManager()?.findFirstCompletelyVisibleItemPosition()
            ?.takeIf { it != RecyclerView.NO_POSITION }
            ?.let { imagesAdapter.getModel(it) }

    private fun scrollToPosition(position: Int) {
        rv_items?.post { rv_items?.scrollToPosition(position)?.also { onImageSelect(position) } }
    }

    private fun showControls(isVisible: Boolean) {
        showImageControls(isVisible)
        ibtn_add_image.changeVisibility(isVisible = isVisible)
        ibtn_settings.changeVisibility(isVisible = isVisible)
        ll_profile_header.changeVisibility(isVisible = isVisible)
        showDotTabs(isVisible = isVisible)
    }

    private fun showImageControls(isVisible: Boolean) {
        ibtn_delete_image.changeVisibility(isVisible = isVisible)
        ibtn_profile_edit.changeVisibility(isVisible = isVisible)
        label_online_status.changeVisibility(isVisible = isVisible)
        ll_left_container.changeVisibility(isVisible = isVisible)
        ll_right_section.changeVisibility(isVisible = isVisible)
        tv_about.changeVisibility(isVisible = isVisible && isAboutVisible())
        tv_status.changeVisibility(isVisible = isVisible)
    }

    // --------------------------------------------------------------------------------------------
    private fun adjustViews() {
        val density = activity?.getScreenDensity() ?: 4.0f
        if (density <= 3.0f) {
            val margin8 = (AppRes.STD_MARGIN_8 * density / 4.5f).toInt()
            val margin16 = (AppRes.STD_MARGIN_16 * density / 4.5f).toInt()
            ibtn_add_image.setPadding(margin16, margin8, margin16, margin8)
            ibtn_add_image_debug.setPadding(margin16, margin8, margin16, margin8)
            ibtn_profile_edit.setPadding(margin16, margin8, margin16, margin8)
            ibtn_settings.setPadding(margin16, margin8, margin16, margin8)
        }
    }

    private fun showDotTabs(isVisible: Boolean) {
        tabs2?.post { tabs2?.changeVisibility(isVisible = isVisible && imagesAdapter.itemCount > 1, soft = true) }
    }

    @DebugOnly
    private fun simulateParticles(count: Int) {
        if (count <= 0) {
            return
        }
        communicator((IBaseMainActivity::class.java))?.let {
            it.showParticleAnimation(id = PARTICLE_TYPE_LIKE, count = count)
            it.showParticleAnimation(id = PARTICLE_TYPE_MATCH, count = count / 10)
            it.showParticleAnimation(id = PARTICLE_TYPE_MESSAGE, count = count / 20)
        }
    }
}