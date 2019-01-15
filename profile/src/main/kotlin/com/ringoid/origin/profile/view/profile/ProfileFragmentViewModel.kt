package com.ringoid.origin.profile.view.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.ringoid.base.view.ViewState
import com.ringoid.base.viewmodel.BaseViewModel
import com.ringoid.domain.interactor.base.Params
import com.ringoid.domain.interactor.image.CreateUserImageUseCase
import com.ringoid.domain.interactor.image.GetUserImagesUseCase
import com.ringoid.domain.model.essence.image.ImageUploadUrlEssenceUnauthorized
import com.ringoid.domain.model.image.UserImage
import com.ringoid.origin.ScreenHelper
import com.ringoid.origin.navigation.ExternalNavigator
import com.ringoid.utility.extension
import com.uber.autodispose.lifecycle.autoDisposable
import timber.log.Timber
import javax.inject.Inject

class ProfileFragmentViewModel @Inject constructor(
    private val createUserImageUseCase: CreateUserImageUseCase, private val getUserImagesUseCase: GetUserImagesUseCase,
    app: Application) : BaseViewModel(app) {

    val images by lazy { MutableLiveData<List<UserImage>>() }

    fun getUserImages() {
        val params = Params().put(ScreenHelper.getLargestPossibleImageResolution(context))

        getUserImagesUseCase.source(params = params)
            .doOnSubscribe { viewState.value = ViewState.LOADING }
            .doOnSuccess { viewState.value = ViewState.IDLE }
            .doOnError { viewState.value = ViewState.ERROR(it) }
            .autoDisposable(this)
            .subscribe({ images.value = it }, Timber::e)
    }

    fun uploadImage(uri: Uri) {
        val essence = ImageUploadUrlEssenceUnauthorized(extension = uri.extension())

        createUserImageUseCase.source(params = Params().put(essence).put("uri", uri))
            .doOnSubscribe { viewState.value = ViewState.LOADING }
            .doOnSuccess { viewState.value = ViewState.DONE(IMAGE_CREATED) }
            .doOnError { viewState.value = ViewState.ERROR(it) }
            .autoDisposable(this)
            .subscribe({ Timber.d("Successfully uploaded image: $it") }, Timber::e)
    }

    // ------------------------------------------
    fun onAddImage() {
        navigation.value = ExternalNavigator::openGalleryToGetImageFragment
    }
}