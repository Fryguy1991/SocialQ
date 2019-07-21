package com.chrisf.socialq.userinterface.activities

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.dagger.components.ActivityComponentHolder
import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.processor.BaseProcessor
import com.chrisf.socialq.userinterface.App
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

abstract class BaseActivity<State, Action, Processor : BaseProcessor<State, Action>> :
        AppCompatActivity(), ActivityComponentHolder {

    private lateinit var activityComponent: ActivityComponent

    @Inject
    lateinit var processor: Processor

    protected val subscriptions = CompositeDisposable()

    protected val actionStream: PublishRelay<Action> = PublishRelay.create()

    override fun provideActivityComponent(): ActivityComponent {
        return activityComponent
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> {
                // Do nothing
                return false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activityComponent = (application as App).appComponent.activityComponent(ActivityModule(this))
        resolveDependencies(activityComponent)
        super.onCreate(savedInstanceState)

        subscriptions.add(processor.attach(actionStream))
        @Suppress("CheckResult")
        processor.stateObservable
                .doOnSubscribe{ subscriptions.add(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleState)


        // Ensure when app is in recents a white title bar is displayed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var taskDescription: ActivityManager.TaskDescription
            taskDescription = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    ActivityManager.TaskDescription(
                            getString(R.string.app_name),
                            R.mipmap.app_launcher_icon_round,
                            resources.getColor(R.color.White, theme))}
                else -> {
                    val color = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> resources.getColor(R.color.White, theme)
                        else -> resources.getColor(R.color.White)
                    }

                    val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.app_launcher_icon_round)
                    ActivityManager.TaskDescription(getString(R.string.app_name), bitmap, color)
                }
            }
            setTaskDescription(taskDescription)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptions.clear()
    }

    abstract fun resolveDependencies(activityComponent: ActivityComponent)

    abstract fun handleState(state: State)
}