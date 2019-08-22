package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.ProcessorModule
import com.chrisf.socialq.dagger.modules.ServiceModule
import com.chrisf.socialq.services.ClientService
import com.chrisf.socialq.services.HostService
import dagger.Subcomponent

@Subcomponent(modules = [ServiceModule::class, ProcessorModule::class])

interface ServiceComponent {
    fun inject(hostService: HostService)
    fun inject(clientService: ClientService)
}