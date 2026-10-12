package org.arkikeskus.launcher.data.ai

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.ConversationActions
import android.view.textclassifier.TextClassifier
import android.app.RemoteInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.StatusNotification
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiNotificationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Uses Android's native on-device TextClassifier (Android 10+) to generate Smart Replies
     * for a given conversation. Completely offline and privacy-safe.
     */
    suspend fun generateSmartReplies(notification: StatusNotification): List<CharSequence> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext emptyList()
        val text = notification.text ?: return@withContext emptyList()

        runCatching {
            val tcm = context.getSystemService(TextClassificationManager::class.java)
            val classifier = tcm?.textClassifier ?: return@withContext emptyList()

            val conversation = ConversationActions.Message.Builder(
                ConversationActions.Message.PERSON_USER_OTHERS
            ).setText(text)
             .setReferenceTime(ZonedDateTime.now())
             .build()

            val request = ConversationActions.Request.Builder(listOf(conversation))
                .setMaxSuggestions(3)
                .build()

            val response = classifier.suggestConversationActions(request)
            response.conversationActions.mapNotNull { it.textReply }
        }.getOrDefault(emptyList())
    }

    /**
     * Future roadmap: Summarize stacked notifications using Gemini Nano (ML Kit GenAI Prompt API).
     */
    suspend fun summarize(notifications: List<StatusNotification>): String? = withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) return@withContext null
        if (notifications.size == 1) return@withContext notifications.first().text

        // ML Kit Prompt API integration placeholder.
        // val generativeModel = Generation.getClient()
        // val request = GenerateContentRequest.Builder(TextPart(prompt)).build()
        // val response = generativeModel.generateContent(request)
        // return response.candidates[0].text

        return@withContext "${notifications.size} new messages"
    }
}
