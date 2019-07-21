package com.chrisf.socialq.processor

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

abstract class BaseProcessor<State, Action>(
        private val lifecycle: Lifecycle,
        val subscriptions: CompositeDisposable
) : LifecycleObserver {

    init {
        lifecycle.addObserver(this)
    }
    /**
     * Relay to push out states
     */
    protected val stateStream: BehaviorRelay<State> = BehaviorRelay.create()

    /**
     * Observable for views to listen for state changes
     */
    val stateObservable: Observable<State>
        get() = stateStream.observeOn(AndroidSchedulers.mainThread())

    /**
     * Subscribes the processor to the view's action stream
     */
    fun attach(actionStream: Observable<Action>): Disposable {
        return actionStream
                .doOnSubscribe { subscriptions.add(it) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { handleAction(it) },
                        { Timber.e(it) })
    }

    /**
     * Acts as a detach for a view with a lifecycle
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestory() {
        subscriptions.clear()
        lifecycle.removeObserver(this)
    }

    abstract fun handleAction(action: Action)
}