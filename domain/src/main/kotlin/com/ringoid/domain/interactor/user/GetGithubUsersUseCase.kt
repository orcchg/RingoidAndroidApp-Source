package com.ringoid.domain.interactor.user

import com.ringoid.domain.executor.UseCasePostExecutor
import com.ringoid.domain.executor.UseCaseThreadExecutor
import com.ringoid.domain.interactor.base.SingleUseCase
import com.ringoid.domain.model.GithubUser
import com.ringoid.domain.repository.IGithubUserRepository
import io.reactivex.Single

class GetGithubUsersUseCase(private val repository: IGithubUserRepository,
                            threadExecutor: UseCaseThreadExecutor, postExecutor: UseCasePostExecutor
)
    : SingleUseCase<List<GithubUser>>(threadExecutor, postExecutor) {

    override fun sourceImpl(): Single<List<GithubUser>> = repository.users()
}
