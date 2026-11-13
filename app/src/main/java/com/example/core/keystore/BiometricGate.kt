package com.example.core.keystore

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface AuthGate {
  fun isDeviceSecure(): Boolean

  suspend fun authenticate(reason: AuthReason): AuthResult
}

class BiometricGate(
  private val context: Context,
  private val activityProvider: () -> FragmentActivity?,
) : AuthGate {
  override fun isDeviceSecure(): Boolean {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    return keyguard?.isDeviceSecure == true
  }

  override suspend fun authenticate(reason: AuthReason): AuthResult {
    if (!isDeviceSecure()) {
      return AuthResult.NoDeviceSecurity
    }
    val activity = activityProvider() ?: return AuthResult.Failed(context.getString(R.string.auth_error_no_activity))
    val allowedAuthenticators =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      }

    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(allowedAuthenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS &&
      canAuth != BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    ) {
      if (!isDeviceSecure()) {
        return AuthResult.NoDeviceSecurity
      }
    }

    val titleRes =
      when (reason) {
        AuthReason.CREATE_WALLET -> R.string.auth_title_create
        AuthReason.UNLOCK_WALLET -> R.string.auth_title_unlock
        AuthReason.VIEW_RECOVERY_PHRASE -> R.string.auth_title_backup
        AuthReason.SIGN_ACTION -> R.string.auth_title_sign
      }
    val subtitleRes =
      when (reason) {
        AuthReason.CREATE_WALLET -> R.string.auth_subtitle_create
        AuthReason.UNLOCK_WALLET -> R.string.auth_subtitle_unlock
        AuthReason.VIEW_RECOVERY_PHRASE -> R.string.auth_subtitle_backup
        AuthReason.SIGN_ACTION -> R.string.auth_subtitle_sign
      }

    return suspendCancellableCoroutine { cont ->
      val executor = ContextCompat.getMainExecutor(activity)
      val callback =
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (cont.isActive) cont.resume(AuthResult.Authenticated)
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (!cont.isActive) return
            val cancelled =
              errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            if (cancelled) {
              cont.resume(AuthResult.Cancelled)
            } else {
              cont.resume(AuthResult.Failed(errString.toString()))
            }
          }
        }
      val prompt = BiometricPrompt(activity, executor, callback)
      val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
          .setTitle(activity.getString(titleRes))
          .setSubtitle(activity.getString(subtitleRes))
          .setAllowedAuthenticators(allowedAuthenticators)
          .build()
      activity.runOnUiThread { prompt.authenticate(promptInfo) }
      cont.invokeOnCancellation { activity.runOnUiThread { prompt.cancelAuthentication() } }
    }
  }
}
