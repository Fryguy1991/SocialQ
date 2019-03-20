package com.chrisf.socialq.business.presenters

import com.chrisf.socialq.userinterface.interfaces.IBaseView

interface IBasePresenter {

    fun attach(view: IBaseView)

    fun detach()

    fun getView() : IBaseView?
}