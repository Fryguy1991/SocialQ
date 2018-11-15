package com.chrisfry.socialq.userinterface.fragments

import android.os.Bundle
import android.support.v4.app.Fragment

open class BaseFragment : Fragment() {
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