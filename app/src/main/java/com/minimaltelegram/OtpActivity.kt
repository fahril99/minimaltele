package com.minimaltelegram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

class OtpActivity : AppCompatActivity() {

    private lateinit var editCode: EditText
    private lateinit var btnVerify: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        editCode = findViewById(R.id.editCode)
        btnVerify = findViewById(R.id.btnVerify)
        txtStatus = findViewById(R.id.txtStatus)

        // Show code info if available
        val state = TdClient.authState
        if (state?.constructor == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
            val codeInfo = (state as TdApi.AuthorizationStateWaitCode).codeInfo
            val infoText = findViewById<TextView>(R.id.txtInfo)
            infoText.text = "Code sent via ${codeInfo?.type?.javaClass?.simpleName ?: "Telegram"}"
        }

        btnVerify.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isEmpty()) {
                txtStatus.text = "Enter the code"
                return@setOnClickListener
            }
            btnVerify.isEnabled = false
            txtStatus.text = "Verifying..."
            TdClient.sendCode(code)
        }

        TdClient.onError = { error ->
            txtStatus.text = error
            btnVerify.isEnabled = true
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
            btnVerify.isEnabled = true
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                startActivity(Intent(this, PasswordActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                startActivity(Intent(this, ChatListActivity::class.java))
                finish()
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                // Auth reset, go back to login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}
