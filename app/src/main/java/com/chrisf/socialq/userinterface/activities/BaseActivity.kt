package com.chrisf.socialq.userinterface.activities

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.dagger.components.ActivityComponentHolder
import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.processor.BaseProcessor
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.common.AlertDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import javax.inject.Inject

abstract class BaseActivity<State, Action, Processor : BaseProcessor<State, Action>> :
    AppCompatActivity(), ActivityComponentHolder {

    abstract val FRAGMENT_HOLDER_ID: Int

    private lateinit var activityComponent: ActivityComponent

    @Inject
    lateinit var processor: Processor

    protected val subscriptions = CompositeDisposable()

    protected val actionStream: PublishRelay<Action> = PublishRelay.create()

    private var alertDialog: AlertDialog? = null

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
        processor.stateObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::handleState)
            .addTo(subscriptions)


        // Ensure when app is in recents a white title bar is displayed
        val taskDescription: ActivityManager.TaskDescription
        taskDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    R.mipmap.app_launcher_icon_round,
                    resources.getColor(R.color.White, theme))
            }
            else -> {
                val color = resources.getColor(R.color.White, theme)
                val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.app_launcher_icon_round)
                ActivityManager.TaskDescription(getString(R.string.app_name), bitmap, color)
            }
        }
        setTaskDescription(taskDescription)
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptions.clear()
    }

    protected fun addFragment(fragment: Fragment) {
        addFragment(fragment, null)
    }

    protected fun addFragment(fragment: Fragment, transactionId: String?) {
        supportFragmentManager.beginTransaction()
            .add(FRAGMENT_HOLDER_ID, fragment, transactionId)
            .commit()
    }

    protected fun addFragmentToBackstack(fragment: Fragment) {
        addFragmentToBackstack(fragment, null)
    }

    protected fun addFragmentToBackstack(fragment: Fragment, transactionId: String?) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.scale_slide_in_from_right,
                R.anim.scale_slide_out_to_left,
                R.anim.scale_slide_in_from_left,
                R.anim.scale_slide_out_to_right)
            .replace(FRAGMENT_HOLDER_ID, fragment)
            .addToBackStack(transactionId)
            .commit()
    }

    /**
     * Display an alert dialog with the prescribed [AlertDialogBinding]
     */
    protected fun showAlertDialog(binding: AlertDialogBinding<Action>) {
        if (alertDialog != null) return

        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(binding.title)
            .setMessage(binding.message)
            .setOnCancelListener {
                binding.cancelAction?.let { actionStream.accept(it) }
            }
            .setOnDismissListener { alertDialog = null }
            .setPositiveButton(
                binding.positiveButtonText
            ) { _, _ ->
                val positiveAction = binding.positiveAction
                positiveAction?.let { actionStream.accept(it) }
            }

        binding.negativeButtonText?.let { negativeButtonText ->
            dialogBuilder.setNegativeButton(
                negativeButtonText
            ) { _, _ ->
                val negativeAction = binding.negativeAction
                negativeAction?.let { actionStream.accept(it) }
            }
        }

        binding.neutralButtonText?.let { neutralButtonText ->
            dialogBuilder.setNegativeButton(
                neutralButtonText
            ) { _, _ ->
                val neutralAction = binding.neutralAction
                neutralAction?.let { actionStream.accept(it) }
            }
        }

        val dialog = dialogBuilder.create()
        dialog.setCancelable(binding.isCancelable)
        dialog.setCanceledOnTouchOutside(binding.isCancelable)
        alertDialog = dialog
        alertDialog?.show()
    }

    abstract fun resolveDependencies(activityComponent: ActivityComponent)

    abstract fun handleState(state: State)
}