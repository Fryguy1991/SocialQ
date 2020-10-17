package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.JobServiceModule
import com.chrisf.socialq.dagger.modules.ProcessorModule
import com.chrisf.socialq.dagger.modules.ServiceModule
import com.chrisf.socialq.services.AccessService
import com.chrisf.socialq.services.ClientService
import com.chrisf.socialq.services.HostService
import dagger.Subcomponent

@Subcomponent(modules = [ServiceModule::class, ProcessorModule::class])
interface ServiceComponent {
    fun inject(hostService: HostService)
    fun inject(clientService: ClientService)
}

@Subcomponent(modules = [JobServiceModule::class, ProcessorModule::class])
interface JobServiceComponent {
    fun inject(accessService: AccessService)
}