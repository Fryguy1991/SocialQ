package com.chrisfry.socialq.business.presenters

import com.chrisfry.socialq.userinterface.interfaces.IBaseView

abstract class BasePresenter : IBasePresenter {
    protected var presenterView: IBaseView? = null

    override fun attach(view: IBaseView) {
        this.presenterView = view
        view.initiateView()
    }

    override fun detach() {
        presenterView = null
    }
}