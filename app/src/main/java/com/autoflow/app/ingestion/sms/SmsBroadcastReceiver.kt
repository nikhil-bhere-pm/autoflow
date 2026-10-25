package com.autoflow.app.ingestion.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.autoflow.app.AutoFlowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * BroadcastReceiver listening for real-time incoming SMS messages (SMS_RECEIVED).
 *
 * Implements:
 * 1. Listening for new incoming SMS only.
 * 2. Uses goAsync() with bounded background processing (8s timeout).
 * 3. Handles multipart SMS by grouping and concatenating parts in proper sequence.
 * 4. Zero raw SMS persistence and privacy-safe logging (no bodies, OTPs, or full accounts).
 * 5. Bounded, non-blocking execution delegating to SmsMessageProcessor.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract SMS from intent: ${e.javaClass.simpleName}")
            return
        } ?: return

        if (messages.isEmpty()) {
            return
        }

        // Group multipart parts by originating address / sender
        val groupedMessages = messages.groupBy {
            it.displayOriginatingAddress ?: it.originatingAddress ?: "UNKNOWN"
        }

        val app = context.applicationContext as? AutoFlowApplication
        if (app == null) {
            Log.e(TAG, "ApplicationContext is not AutoFlowApplication")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    for ((sender, parts) in groupedMessages) {
                        // Concatenate multipart message chunks in order
                        val fullBody = parts.joinToString(separator = "") {
                            it.displayMessageBody ?: it.messageBody ?: ""
                        }
                        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                        processIncomingSmsMessage(
                            context = context,
                            app = app,
                            sender = sender,
                            body = fullBody,
                            timestamp = timestamp
                        )
                    }
                }
            } catch (e: Exception) {
                // Log exception type only; never log sensitive message text
                Log.w(TAG, "Background SMS processing encountered: ${e.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoFlow_SmsReceiver"
        private const val TIMEOUT_MS = 8000L

        suspend fun processIncomingSmsMessage(
            context: Context,
            app: AutoFlowApplication,
            sender: String,
            body: String,
            timestamp: Long = System.currentTimeMillis()
        ): ProcessSmsResult {
            val processor = SmsMessageProcessor(app.ingestionPipeline)
            val result = processor.processMessage(
                sender = sender,
                body = body,
                timestamp = timestamp
            )
            if (result is ProcessSmsResult.Success) {
                com.autoflow.app.notification.CapturedExpenseNotificationHelper.notifyIfEnabled(
                    context = context,
                    transactionId = result.transactionId
                )
            }
            return result
        }
    }
}
