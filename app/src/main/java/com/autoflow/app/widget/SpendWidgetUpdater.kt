package com.autoflow.app.widget

import android.content.Context

object SpendWidgetUpdater {

    var updateHook: ((Context) -> Unit)? = { context ->
        AutoFlowSpendWidgetProvider.updateAllWidgets(context)
    }

    fun notifyDataChanged(context: Context) {
        updateHook?.invoke(context)
    }
}
