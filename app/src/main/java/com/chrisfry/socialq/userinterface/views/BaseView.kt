package com.chrisfry.socialq.userinterface.views

import com.chrisfry.socialq.business.presenters.IBasePresenter
import com.chrisfry.socialq.userinterface.interfaces.IBaseView

open class BaseView : IBaseView {
    lateinit var viewPresenter: IBasePresenter

    override fun setPresenter(presenter: IBasePresenter) {
        viewPresenter = presenter
    }
}