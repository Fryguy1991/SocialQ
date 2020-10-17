package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.chrisf.socialq.dagger.components.ActivityComponentHolder
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.dagger.modules.FragmentModule
import com.chrisf.socialq.processor.BaseProcessor
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

abstract class BaseFragment<State, Action, Processor : BaseProcessor<State, Action>> : Fragment() {

    private lateinit var fragmentComponent: FragmentComponent

    protected val subscriptions = CompositeDisposable()
    protected val actionStream: PublishRelay<Action> = PublishRelay.create()

    @Inject
    lateinit var processor: Processor


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fragmentComponent = (activity as ActivityComponentHolder).provideActivityComponent().fragmentComponent(FragmentModule((this)))
        resolveDepencencies(fragmentComponent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        subscriptions.add(processor.attach(actionStream))

        @Suppress("CheckResult")
        processor.stateObservable
                .doOnSubscribe{ subscriptions.add(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        subscriptions.clear()
    }

    abstract fun handleState(state: State)

    abstract fun resolveDepencencies(component: FragmentComponent)
}