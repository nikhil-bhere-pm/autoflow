package com.autoflow.app.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.ingestion.sms.SmsBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QaFakeSmsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INJECT_FAKE_SMS = "com.autoflow.app.qa.ACTION_INJECT_FAKE_SMS"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_BODY = "body"
        const val EXTRA_PRESET = "preset"

        // Masked, clearly fake bank SMS messages
        const val PRESET_UNKNOWN_MERCHANT = "Rs 320.00 debited from A/c XX1234 on 25-Oct-26 to UNKNOWN MERCHANT UPI Ref 000000"
        const val PRESET_KNOWN_MERCHANT = "Rs 130.00 debited from A/c XX1234 on 25-Oct-26 to Swiggy UPI Ref 000001"
        const val PRESET_TWO_LIKELY = "Rs 450.00 debited from A/c XX1234 on 25-Oct-26 to Apollo Pharmacy UPI Ref 000002"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_INJECT_FAKE_SMS) return

        val app = context.applicationContext as? AutoFlowApplication ?: return

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: "VM-HDFCBK"
        val preset = intent.getStringExtra(EXTRA_PRESET)
        val body = when (preset) {
            "unknown" -> PRESET_UNKNOWN_MERCHANT
            "known" -> PRESET_KNOWN_MERCHANT
            "two_likely" -> PRESET_TWO_LIKELY
            else -> intent.getStringExtra(EXTRA_BODY) ?: PRESET_UNKNOWN_MERCHANT
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                // Feeds into the exact same function the real SMS receiver calls
                SmsBroadcastReceiver.processIncomingSmsMessage(
                    context = context,
                    app = app,
                    sender = sender,
                    body = body,
                    timestamp = System.currentTimeMillis()
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
