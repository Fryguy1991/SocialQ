package com.chrisf.socialq.services

import android.app.job.JobParameters
import com.chrisf.socialq.dagger.components.JobServiceComponent
import com.chrisf.socialq.processor.AccessProcessor
import com.chrisf.socialq.processor.AccessProcessor.AccessAction
import com.chrisf.socialq.processor.AccessProcessor.AccessAction.RequestAccessRefresh
import com.chrisf.socialq.processor.AccessProcessor.AccessState
import com.chrisf.socialq.processor.AccessProcessor.AccessState.AccessRefreshComplete

/**
 * Although automatic access token refresh has been added to the network service, that will not ensure that we always
 * have a valid access token (due to possible time between network calls). The Spotify player requires a valid access
 * token at all times, hence the need for this service
 */
class AccessService : BaseJobService<AccessState, AccessAction, AccessProcessor>() {

    override fun resolveDependencies(jobServiceComponent: JobServiceComponent) {
        jobServiceComponent.inject(this)
    }

    override fun handleState(state: AccessState) {
        when (state) {
            is AccessRefreshComplete -> jobFinished(state.jobParameters, true)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        actionStream.accept(RequestAccessRefresh(params))
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopSelf()
        return true
    }

    companion object {
        const val ACCESS_SERVICE_ID = 3
    }
}