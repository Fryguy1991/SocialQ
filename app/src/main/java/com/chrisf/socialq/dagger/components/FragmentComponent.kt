package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.FragmentModule
import com.chrisf.socialq.userinterface.fragments.SearchResultsFragment
import dagger.Subcomponent

@Subcomponent(modules = [FragmentModule::class])
interface FragmentComponent {
    fun inject(fragment: SearchResultsFragment)
}