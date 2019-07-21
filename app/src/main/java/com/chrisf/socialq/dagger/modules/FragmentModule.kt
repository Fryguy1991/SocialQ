package com.chrisf.socialq.dagger.modules

import com.chrisf.socialq.userinterface.fragments.BaseFragment
import dagger.Module

@Module
class FragmentModule(private val fragment: BaseFragment<*, *, *>) {
    // Add something needed from fragment here
}