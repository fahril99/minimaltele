package com.minimaltelegram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

class PasswordActivity : AppCompatActivity() {

    private lateinit var editPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        editPassword = findViewById(R.id.editPassword)
        btnSubmit = findViewById(R.id.btnSubmit)
        txtStatus = findViewById(R.id.txtStatus)

        // Show password hint if available
        val state = TdClient.authState
        if (state?.constructor == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR) {
            val hint = (state as TdApi.AuthorizationStateWaitPassword).passwordHint
            if (!hint.isNullOrEmpty()) {
                val txtHint = findViewById<TextView>(R.id.txtHint)
                txtHint.text = "Hint: $hint"
            }
        }

        btnSubmit.setOnClickListener {
            val password = editPassword.text.toString()
            if (password.isEmpty()) {
                txtStatus.text = "Enter your password"
                return@setOnClickListener
            }
            btnSubmit.isEnabled = false
            txtStatus.text = "Checking..."
            TdClient.sendPassword(password)
        }

        TdClient.onError = { error ->
            txtStatus.text = error
            btnSubmit.isEnabled = true
        }

        TdClient.onAuthStateChanged = { state ->
            handleAuthState(state)
        }
    }

    override fun onResume() {
        super.onResume()
        TdClient.onAuthStateChanged = { state -> handleAuthState(state) }
        TdClient.onError = { error ->
            txtStatus.text = error
            btnSubmit.isEnabled = true
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                startActivity(Intent(this, ChatListActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}
