package com.chrisf.socialq.dagger.modules

import com.chrisf.socialq.services.BaseJobService
import com.chrisf.socialq.services.BaseService
import dagger.Module

@Module
class ServiceModule(private val service: BaseService<*, *, *>)

@Module
class JobServiceModule(private val service: BaseJobService<*, *, *>)