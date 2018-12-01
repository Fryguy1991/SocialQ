package com.chrisfry.socialq.userinterface.interfaces

import com.chrisfry.socialq.business.presenters.IBasePresenter

interface IBaseView{

    fun setPresenter(presenter: IBasePresenter)
}