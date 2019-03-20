package com.chrisf.socialq.business.presenters

import com.chrisf.socialq.userinterface.interfaces.IBaseView

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