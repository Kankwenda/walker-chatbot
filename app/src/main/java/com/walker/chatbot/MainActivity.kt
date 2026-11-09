package com.walker.chatbot

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Walker Chatbot — discute avec Pollinations (gratuit, sans clé).
 *
 * Fonctionnement :
 * 1) Sans clé : GET https://text.pollinations.ai/<question>, 1 requête / 30 s.
 * 2) Avec clé publiable (pk_) : la clé est envoyée en en-tête
 *    « Authorization: Bearer <clé> » et lève la limite de débit.
 *    La clé est tapée par l'utilisateur dans l'appli et gardée
 *    uniquement sur son téléphone (SharedPreferences).
 *    JAMAIS de clé secrète (sk_) dans une appli : elle donne accès
 *    à tout le compte Pollinations et serait volée en minutes.
 */
class MainActivity : Activity() {

    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var questionInput: EditText
    private lateinit var sendButton: Button
    private lateinit var keyRow: LinearLayout
    private lateinit var keyInput: EditText
    private lateinit var keyStatus: TextView
    private lateinit var statusText: TextView

    private var lastCallMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messagesContainer = findViewById(R.id.messagesContainer)
        scrollView = findViewById(R.id.scrollView)
        questionInput = findViewById(R.id.questionInput)
        sendButton = findViewById(R.id.btnSend)
        keyRow = findViewById(R.id.keyRow)
        keyInput = findViewById(R.id.keyInput)
        keyStatus = findViewById(R.id.keyStatus)
        statusText = findViewById(R.id.statusText)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_POLL, "") ?: ""
        keyInput.setText(savedKey)
        refreshKeyStatus(savedKey)

        findViewById<Button>(R.id.btnKey).setOnClickListener {
            keyRow.visibility = if (keyRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.btnSaveKey).setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.startsWith("sk_")) {
                Toast.makeText(
                    this,
                    "Refusé : une clé sk_ est secrète, jamais dans une appli. Utilise une clé pk_.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            prefs.edit().putString(KEY_POLL, key).apply()
            refreshKeyStatus(key)
            keyRow.visibility = View.GONE
            Toast.makeText(this, "Clé enregistrée sur ce téléphone uniquement.", Toast.LENGTH_SHORT).show()
        }

        sendButton.setOnClickListener { sendQuestion() }
        questionInput.setOnEditorActionListener { _, _, _ ->
            sendButton.performClick()
            true
        }

        addMessage(
            "Salut ! Pose-moi une question courte et simple.\n" +
                "Je réponds avec Pollinations. Sans clé, attends 30 s entre deux questions.\n" +
                "Vérifie toujours mes réponses, je peux inventer.",
            false
        )
    }

    private fun refreshKeyStatus(key: String) {
        keyStatus.text = if (key.isBlank()) {
            "Sans clé : 1 question toutes les 30 s."
        } else {
            "Clé pk_ enregistrée ✅ — limites levées."
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this)
        bubble.text = text
        bubble.setTextColor(if (isUser) Color.WHITE else Color.parseColor("#E2E8F0"))
        bubble.setBackgroundColor(if (isUser) Color.parseColor("#2563EB") else Color.parseColor("#1E293B"))
        bubble.setPadding(24, 18, 24, 18)
        bubble.textSize = 15f
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = if (isUser) Gravity.END else Gravity.START
        params.setMargins(0, 8, 0, 8)
        bubble.layoutParams = params
        messagesContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun sendQuestion() {
        val question = questionInput.text.toString().trim()
        if (question.isEmpty()) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_POLL, "") ?: ""

        if (key.isBlank()) {
            val waitMs = DELAY_FREE_MS - (System.currentTimeMillis() - lastCallMs)
            if (waitMs > 0) {
                Toast.makeText(
                    this,
                    "Attends encore ${waitMs / 1000 + 1} s (limite gratuite). Avec une clé pk_, pas d'attente.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        lastCallMs = System.currentTimeMillis()

        addMessage(question, true)
        questionInput.text.clear()
        sendButton.isEnabled = false
        setStatus("Je cherche...")

        thread {
            try {
                val encoded = URLEncoder.encode(question, "UTF-8")
                val url = URL("https://text.pollinations.ai/$encoded")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                if (key.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer $key")
                }
                val code = conn.responseCode
                if (code == 402 || code == 429) {
                    val retry = conn.getHeaderField("Retry-After") ?: "30"
                    runOnUiThread {
                        addMessage(
                            "Pollinations est saturé (erreur $code). Réessaie dans $retry s, ou ajoute une clé pk_ en haut.",
                            false
                        )
                    }
                } else if (code != 200) {
                    runOnUiThread { addMessage("Pollinations a répondu une erreur $code. Réessaie.", false) }
                } else {
                    val text = BufferedReader(InputStreamReader(conn.inputStream)).readText().trim()
                    runOnUiThread {
                        if (text.isEmpty()) {
                            addMessage("Pollinations a répondu vide. Réessaie.", false)
                        } else {
                            addMessage(text, false)
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage(
                        "Pas de réponse : vérifie ta connexion internet.\n(${e.message})",
                        false
                    )
                }
            } finally {
                runOnUiThread {
                    sendButton.isEnabled = true
                    setStatus("")
                }
            }
        }
    }

    companion object {
        private const val PREFS = "walker_chatbot"
        private const val KEY_POLL = "pollinations_key"
        private const val DELAY_FREE_MS = 30_000L
    }
}
