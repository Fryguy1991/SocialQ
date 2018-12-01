package com.chrisfry.socialq.business.presenters

import com.chrisfry.socialq.userinterface.interfaces.IBaseView

interface IBasePresenter<in V : IBaseView> {

    fun attach(view: V)

    fun detach()
}