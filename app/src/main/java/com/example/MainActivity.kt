package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.VaultState
import com.example.ui.screens.BackupVerifyScreen
import com.example.ui.screens.LockedVaultScreen
import com.example.ui.screens.PinPromptDialog
import com.example.ui.screens.RecoveryPhraseScreen
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.screens.WelcomeCreateScreen
import com.example.ui.theme.GramVaultTheme

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    setContent {
      GramVaultTheme(darkTheme = true) {
        val viewModel: WalletViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        DisposableEffect(this) {
          viewModel.attachActivity(this@MainActivity)
          onDispose { viewModel.attachActivity(null) }
        }

        uiState.pinPromptMode?.let { mode ->
          PinPromptDialog(
            isSetupMode = mode == PinPromptMode.SETUP_FOR_CREATE,
            errorMessage = uiState.pinPromptError,
            onDismiss = viewModel::dismissPinPrompt,
            onSubmitPin = viewModel::submitPin,
          )
        }

        when (val vault = uiState.vaultState) {
          is VaultState.Uninitialized -> {
            WelcomeCreateScreen(
              isDeviceSecure = uiState.isDeviceSecure,
              isBusy = uiState.isBusy,
              errorMessage = uiState.errorMessage,
              onCreateWallet = viewModel::createWallet,
            )
          }

          is VaultState.Locked -> {
            LockedVaultScreen(
              isKeyInvalidated = false,
              isBusy = uiState.isBusy,
              errorMessage = uiState.errorMessage,
              onUnlock = viewModel::unlockWallet,
              onResetVault = viewModel::resetVault,
            )
          }

          is VaultState.KeyInvalidated -> {
            LockedVaultScreen(
              isKeyInvalidated = true,
              isBusy = uiState.isBusy,
              errorMessage = uiState.errorMessage,
              onUnlock = viewModel::unlockWallet,
              onResetVault = viewModel::resetVault,
            )
          }

          is VaultState.Unlocked -> {
            when (uiState.subScreen) {
              ActiveSubScreen.RECOVERY_PHRASE -> {
                RecoveryPhraseScreen(
                  words = uiState.phraseWordsForDisplay,
                  isAlreadyBackedUp = vault.metadata.isBackedUp,
                  onBack = viewModel::closeSubScreens,
                  onProceedToVerify = viewModel::proceedFromPhraseScreen,
                  onSkipBackup = viewModel::closeSubScreens,
                )
              }

              ActiveSubScreen.BACKUP_VERIFY -> {
                val challenge = uiState.backupChallenge
                if (challenge != null) {
                  BackupVerifyScreen(
                    challenge = challenge,
                    isBusy = uiState.isBusy,
                    errorMessage = uiState.errorMessage,
                    onBack = viewModel::backToPhraseOrHome,
                    onSubmit = viewModel::confirmBackup,
                  )
                }
              }

              ActiveSubScreen.NONE -> {
                WalletHomeScreen(
                  metadata = vault.metadata,
                  activeNetwork = vault.activeNetwork,
                  bannerMessage = uiState.bannerMessage,
                  onClearBanner = viewModel::clearBanner,
                  onSelectNetwork = viewModel::selectNetwork,
                  onOpenBackup = viewModel::openBackupFlow,
                  onLockWallet = viewModel::lockWallet,
                )
              }
            }
          }
        }
      }
    }
  }
}
