package com.chrisf.socialq.services

import android.app.Service
import com.chrisf.socialq.dagger.components.ServiceComponent
import com.chrisf.socialq.dagger.modules.ServiceModule
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.processor.BaseProcessor
import com.chrisf.socialq.userinterface.App
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

abstract class BaseService<State, Action, Processor : BaseProcessor<State, Action>> : Service() {

    private lateinit var serviceComponent: ServiceComponent

    @Inject
    lateinit var processor: Processor


    protected val subscriptions = CompositeDisposable()

    protected val actionStream: PublishRelay<Action> = PublishRelay.create()

    override fun onCreate() {
        serviceComponent = (application as App).appComponent.serviceComponent(ServiceModule(this))
        resolveDependencies(serviceComponent)
        super.onCreate()

        subscriptions.add(processor.attach(actionStream))
        processor.stateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleState)
                .addTo(subscriptions)
    }

    override fun onDestroy() {
        processor.detach()
        subscriptions.clear()
        super.onDestroy()
    }

    abstract fun resolveDependencies(serviceComponent: ServiceComponent)

    abstract fun handleState(state: State)
}