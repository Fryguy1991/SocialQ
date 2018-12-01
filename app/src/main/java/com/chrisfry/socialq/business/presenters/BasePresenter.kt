package com.chrisfry.socialq.business.presenters

import com.chrisfry.socialq.userinterface.interfaces.IBaseView

open class BasePresenter : IBasePresenter{
    protected var view: IBaseView? = null

    override fun attach(view: IBaseView) {
        this.view = view
    }

    override fun detach() {
        view = null
    }
}