package com.minimaltelegram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

class LoginActivity : AppCompatActivity() {

    private lateinit var editPhone: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editPhone = findViewById(R.id.editPhone)
        btnLogin = findViewById(R.id.btnLogin)
        txtStatus = findViewById(R.id.txtStatus)

        btnLogin.setOnClickListener {
            val phone = editPhone.text.toString().trim()
            if (phone.isEmpty()) {
                txtStatus.text = "Enter a phone number"
                return@setOnClickListener
            }
            btnLogin.isEnabled = false
            txtStatus.text = "Sending..."
            TdClient.sendPhoneNumber(phone)
        }

        TdClient.onError = { error ->
            txtStatus.text = error
            btnLogin.isEnabled = true
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
            btnLogin.isEnabled = true
        }

        // Check current state (e.g., already logged in)
        TdClient.authState?.let { handleAuthState(it) }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                startActivity(Intent(this, OtpActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                startActivity(Intent(this, PasswordActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                startActivity(Intent(this, ChatListActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                // Stay on this screen
                btnLogin.isEnabled = true
                txtStatus.text = ""
            }
        }
    }
}
