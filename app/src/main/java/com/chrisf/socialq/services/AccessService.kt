package com.chrisf.socialq.services

import android.app.job.JobParameters
import com.chrisf.socialq.dagger.components.JobServiceComponent
import com.chrisf.socialq.processor.AccessProcessor
import com.chrisf.socialq.processor.AccessProcessor.AccessAction
import com.chrisf.socialq.processor.AccessProcessor.AccessState

class AccessService : BaseJobService<AccessState, AccessAction, AccessProcessor>() {

    override fun resolveDependencies(jobServiceComponent: JobServiceComponent) {
        jobServiceComponent.inject(this)
    }

    override fun handleState(state: AccessState) {
        TODO("Currently not receiving any states")
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        actionStream.accept(AccessAction.RequestAccessRefresh)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}