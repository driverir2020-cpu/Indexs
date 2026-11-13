package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun SecureScreenEffect() {
  val context = LocalContext.current
  DisposableEffect(context) {
    val activity = context.findActivity()
    val window = activity?.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
