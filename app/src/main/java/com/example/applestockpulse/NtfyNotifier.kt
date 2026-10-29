package com.example.applestockpulse

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where to publish: the ntfy server root and the topic on it. */
data class NtfyTarget(val serverUrl: String, val topic: String)

/**
 * Sends each alarm to an ntfy topic (https://ntfy.sh), so a laptop, tablet or watch that
 * subscribes to the topic alerts too.
 */
object NtfyNotifier {

    private const val TAG = "NtfyNotifier"

    const val DEFAULT_SERVER = "https://ntfy.sh"

    // ntfy's own topic rule
    private val TOPIC_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * "my-topic" -> ntfy.sh/my-topic, "https://ntfy.example.com/my-topic" -> own server.
     * Null when blank or not usable (bad topic name, or not https).
     */
    fun parseTarget(raw: String): NtfyTarget? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) {
            return if (TOPIC_REGEX.matches(text)) NtfyTarget(DEFAULT_SERVER, text) else null
        }

        val url = text.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        val segments = url.pathSegments
        val topicIndex = segments.indexOfLast { it.isNotEmpty() }
        if (topicIndex < 0) return null
        val topic = segments[topicIndex]
        if (!TOPIC_REGEX.matches(topic)) return null

        val server = url.newBuilder()
            .removePathSegment(topicIndex)
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
        return NtfyTarget(server, topic)
    }

    /**
     * Publishes at urgent priority. Runs in the background; [onResult] gets (success, message)
     * on an OkHttp thread.
     */
    fun publish(
        target: NtfyTarget?,
        title: String,
        message: String,
        clickUrl: String?,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (target == null) return

        val json = JSONObject()
            .put("topic", target.topic)
            .put("title", title)
            .put("message", message)
            .put("priority", 5)
            .put("tags", JSONArray().put("rotating_light"))
        if (clickUrl != null) json.put("click", clickUrl)

        // JSON publishing goes to the server root, which allows emoji in the title
        val request = try {
            Request.Builder()
                .url("${target.serverUrl}/")
                .post(json.toString().toRequestBody(JSON_TYPE))
                .build()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad ntfy server URL ${target.serverUrl}", e)
            onResult?.invoke(false, "Bad ntfy server URL")
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "ntfy publish failed", e)
                onResult?.invoke(false, "ntfy failed: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.i(TAG, "ntfy alert sent to ${target.topic}")
                        onResult?.invoke(true, "Sent to ntfy topic ${target.topic}")
                    } else {
                        Log.w(TAG, "ntfy answered HTTP ${it.code}")
                        onResult?.invoke(false, "ntfy answered HTTP ${it.code}")
                    }
                }
            }
        })
    }
}
