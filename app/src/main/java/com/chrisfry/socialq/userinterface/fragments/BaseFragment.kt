package com.chrisfry.socialq.userinterface.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment

open class BaseFragment : androidx.fragment.app.Fragment() {
    companion object {
        fun newInstance(args: Bundle) : BaseFragment {
            val newFragment = BaseFragment()
            newFragment.arguments = args
            return newFragment
        }
    }

    open fun handleOnBackPressed() : Boolean {
        return false
    }
}