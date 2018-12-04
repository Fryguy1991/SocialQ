package com.chrisfry.socialq.business.presenters

import com.chrisfry.socialq.userinterface.interfaces.IBaseView

interface IBasePresenter {

    fun attach(view: IBaseView)

    fun detach()

    fun getView() : IBaseView?
}