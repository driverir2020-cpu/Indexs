package com.example

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.keystore.BiometricGate
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.model.BackupChallenge
import com.example.core.model.MnemonicPhrase
import com.example.core.model.Network
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import com.example.core.wallet.WalletCore
import com.example.core.wallet.WalletCoreImpl
import java.lang.ref.WeakReference
import java.util.Arrays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveSubScreen {
  NONE,
  RECOVERY_PHRASE,
  BACKUP_VERIFY,
}

enum class PinPromptMode {
  SETUP_FOR_CREATE,
  ENTER_FOR_UNLOCK,
  ENTER_FOR_PHRASE,
  ENTER_FOR_BACKUP_CONFIRM,
}

data class AppUiState(
  val vaultState: VaultState = VaultState.Uninitialized,
  val subScreen: ActiveSubScreen = ActiveSubScreen.NONE,
  val phraseWordsForDisplay: List<String> = emptyList(),
  val backupChallenge: BackupChallenge? = null,
  val pinPromptMode: PinPromptMode? = null,
  val pinPromptError: String? = null,
  val isBusy: Boolean = false,
  val errorMessage: String? = null,
  val bannerMessage: String? = null,
  val isDeviceSecure: Boolean = true,
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {
  private var activityRef: WeakReference<FragmentActivity> = WeakReference(null)
  private val authGate = BiometricGate(application.applicationContext) { activityRef.get() }
  private val walletCore: WalletCore =
    WalletCoreImpl(applicationContext = application.applicationContext, authGate = authGate)

  private var transientPhrase: MnemonicPhrase? = null
  private var pendingConfirmAnswers: Map<Int, CharArray>? = null

  private val _uiState =
    MutableStateFlow(
      AppUiState(
        vaultState = walletCore.vaultState.value,
        isDeviceSecure = walletCore.isDeviceSecure(),
      )
    )
  val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      walletCore.vaultState.collectLatest { vault ->
        _uiState.update { state ->
          val shouldClearSub =
            vault !is VaultState.Unlocked && state.subScreen != ActiveSubScreen.NONE
          if (shouldClearSub) clearTransientPhrase()
          state.copy(
            vaultState = vault,
            subScreen = if (shouldClearSub) ActiveSubScreen.NONE else state.subScreen,
            phraseWordsForDisplay =
              if (shouldClearSub) emptyList() else state.phraseWordsForDisplay,
          )
        }
      }
    }
  }

  fun attachActivity(activity: FragmentActivity?) {
    activityRef = WeakReference(activity)
    _uiState.update { it.copy(isDeviceSecure = walletCore.isDeviceSecure()) }
  }

  fun createWallet() {
    if (_uiState.value.isBusy) return
    if (walletCore.activeSecurityMode() == VaultSecurityMode.APP_PIN_WITH_KEYSTORE) {
      _uiState.update {
        it.copy(pinPromptMode = PinPromptMode.SETUP_FOR_CREATE, pinPromptError = null, errorMessage = null)
      }
      return
    }
    executeCreateWallet(appPin = null)
  }

  private fun executeCreateWallet(appPin: CharArray?) {
    _uiState.update { it.copy(isBusy = true, errorMessage = null, pinPromptError = null) }
    viewModelScope.launch {
      walletCore
        .createWallet(Network.MAINNET, appPin = appPin)
        .onSuccess { newWallet ->
          setTransientPhrase(newWallet.mnemonic)
          _uiState.update {
            it.copy(
              isBusy = false,
              pinPromptMode = null,
              pinPromptError = null,
              subScreen = ActiveSubScreen.RECOVERY_PHRASE,
              phraseWordsForDisplay = extractDisplayWords(newWallet.mnemonic),
              errorMessage = null,
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isBusy = false,
              pinPromptMode = null,
              errorMessage = err.message ?: err::class.java.simpleName,
            )
          }
        }
    }
  }

  fun unlockWallet() {
    if (_uiState.value.isBusy) return
    if (walletCore.activeSecurityMode() == VaultSecurityMode.APP_PIN_WITH_KEYSTORE) {
      _uiState.update {
        it.copy(pinPromptMode = PinPromptMode.ENTER_FOR_UNLOCK, pinPromptError = null, errorMessage = null)
      }
      return
    }
    executeUnlockWallet(appPin = null)
  }

  private fun executeUnlockWallet(appPin: CharArray?) {
    _uiState.update { it.copy(isBusy = true, errorMessage = null, pinPromptError = null) }
    viewModelScope.launch {
      walletCore
        .unlockWallet(Network.MAINNET, appPin = appPin)
        .onSuccess {
          _uiState.update {
            it.copy(isBusy = false, pinPromptMode = null, pinPromptError = null, errorMessage = null)
          }
        }
        .onFailure { err ->
          val stayInPin = _uiState.value.pinPromptMode != null
          _uiState.update {
            it.copy(
              isBusy = false,
              pinPromptError = if (stayInPin) err.message ?: err::class.java.simpleName else null,
              errorMessage = if (!stayInPin) err.message ?: err::class.java.simpleName else null,
            )
          }
        }
    }
  }

  fun openBackupFlow() {
    val unlocked = _uiState.value.vaultState as? VaultState.Unlocked ?: return
    if (_uiState.value.isBusy) return
    executeOpenBackupFlow(appPin = null, fallbackPub = unlocked.metadata.publicKeyHex)
  }

  private fun executeOpenBackupFlow(appPin: CharArray?, fallbackPub: String) {
    _uiState.update { it.copy(isBusy = true, errorMessage = null, pinPromptError = null) }
    viewModelScope.launch {
      walletCore
        .revealRecoveryPhrase(appPin = appPin)
        .onSuccess { phrase ->
          setTransientPhrase(phrase)
          _uiState.update {
            it.copy(
              isBusy = false,
              pinPromptMode = null,
              pinPromptError = null,
              subScreen = ActiveSubScreen.RECOVERY_PHRASE,
              phraseWordsForDisplay = extractDisplayWords(phrase),
              errorMessage = null,
            )
          }
        }
        .onFailure { err ->
          if (err is VaultAuthRequiredException) {
            _uiState.update {
              it.copy(isBusy = false, pinPromptMode = PinPromptMode.ENTER_FOR_PHRASE, pinPromptError = null)
            }
          } else {
            val stayInPin = _uiState.value.pinPromptMode != null
            _uiState.update {
              it.copy(
                isBusy = false,
                pinPromptError = if (stayInPin) err.message ?: err::class.java.simpleName else null,
                bannerMessage = if (!stayInPin) err.message ?: fallbackPub else null,
              )
            }
          }
        }
    }
  }

  fun proceedFromPhraseScreen() {
    val unlocked = _uiState.value.vaultState as? VaultState.Unlocked ?: return
    if (unlocked.metadata.isBackedUp) {
      closeSubScreens()
    } else {
      val challenge = walletCore.createBackupChallenge()
      _uiState.update {
        it.copy(
          subScreen = ActiveSubScreen.BACKUP_VERIFY,
          backupChallenge = challenge,
          phraseWordsForDisplay = emptyList(),
          errorMessage = null,
        )
      }
      clearTransientPhrase()
    }
  }

  fun backToPhraseOrHome() {
    when (_uiState.value.subScreen) {
      ActiveSubScreen.BACKUP_VERIFY -> openBackupFlow()
      ActiveSubScreen.RECOVERY_PHRASE -> closeSubScreens()
      ActiveSubScreen.NONE -> Unit
    }
  }

  fun closeSubScreens() {
    clearTransientPhrase()
    clearPendingConfirmAnswers()
    _uiState.update {
      it.copy(
        subScreen = ActiveSubScreen.NONE,
        phraseWordsForDisplay = emptyList(),
        backupChallenge = null,
        errorMessage = null,
      )
    }
  }

  fun confirmBackup(answers: Map<Int, CharArray>) {
    executeConfirmBackup(answers = answers, appPin = null)
  }

  private fun executeConfirmBackup(answers: Map<Int, CharArray>, appPin: CharArray?) {
    val challenge = _uiState.value.backupChallenge ?: return
    if (_uiState.value.isBusy) {
      answers.values.forEach { Arrays.fill(it, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
      return
    }
    _uiState.update { it.copy(isBusy = true, errorMessage = null, pinPromptError = null) }
    val answersCopy = answers.mapValues { it.value.copyOf() }
    viewModelScope.launch {
      walletCore
        .confirmBackup(challenge, answers, appPin = appPin)
        .onSuccess { matched ->
          clearPendingConfirmAnswers()
          answersCopy.values.forEach { Arrays.fill(it, '\u0000') }
          if (matched) {
            clearTransientPhrase()
            _uiState.update {
              it.copy(
                isBusy = false,
                pinPromptMode = null,
                pinPromptError = null,
                subScreen = ActiveSubScreen.NONE,
                backupChallenge = null,
                phraseWordsForDisplay = emptyList(),
                errorMessage = null,
                bannerMessage = getApplication<Application>().getString(R.string.backup_confirmed_toast),
              )
            }
          } else {
            _uiState.update {
              it.copy(
                isBusy = false,
                pinPromptMode = null,
                errorMessage = getApplication<Application>().getString(R.string.verify_mismatch_error),
              )
            }
          }
        }
        .onFailure { err ->
          if (err is VaultAuthRequiredException) {
            clearPendingConfirmAnswers()
            pendingConfirmAnswers = answersCopy
            _uiState.update {
              it.copy(
                isBusy = false,
                pinPromptMode = PinPromptMode.ENTER_FOR_BACKUP_CONFIRM,
                pinPromptError = null,
              )
            }
          } else {
            answersCopy.values.forEach { Arrays.fill(it, '\u0000') }
            val stayInPin = _uiState.value.pinPromptMode != null
            _uiState.update {
              it.copy(
                isBusy = false,
                pinPromptError = if (stayInPin) err.message ?: err::class.java.simpleName else null,
                errorMessage = if (!stayInPin) err.message ?: err::class.java.simpleName else null,
              )
            }
          }
        }
    }
  }

  fun submitPin(pin: CharArray) {
    when (_uiState.value.pinPromptMode) {
      PinPromptMode.SETUP_FOR_CREATE -> executeCreateWallet(pin)
      PinPromptMode.ENTER_FOR_UNLOCK -> executeUnlockWallet(pin)
      PinPromptMode.ENTER_FOR_PHRASE -> {
        val pub = (_uiState.value.vaultState as? VaultState.Unlocked)?.metadata?.publicKeyHex.orEmpty()
        executeOpenBackupFlow(appPin = pin, fallbackPub = pub)
      }
      PinPromptMode.ENTER_FOR_BACKUP_CONFIRM -> {
        val savedAnswers = pendingConfirmAnswers
        if (savedAnswers != null) {
          val copy = savedAnswers.mapValues { it.value.copyOf() }
          executeConfirmBackup(answers = copy, appPin = pin)
        } else {
          Arrays.fill(pin, '\u0000')
          dismissPinPrompt()
        }
      }
      null -> Arrays.fill(pin, '\u0000')
    }
  }

  fun dismissPinPrompt() {
    clearPendingConfirmAnswers()
    _uiState.update { it.copy(pinPromptMode = null, pinPromptError = null) }
  }

  fun selectNetwork(network: Network) {
    viewModelScope.launch { walletCore.switchNetwork(network) }
  }

  fun lockWallet() {
    closeSubScreens()
    walletCore.lockWallet()
  }

  fun resetVault() {
    closeSubScreens()
    walletCore.resetVault()
  }

  fun clearBanner() {
    _uiState.update { it.copy(bannerMessage = null) }
  }

  private fun setTransientPhrase(phrase: MnemonicPhrase) {
    transientPhrase?.wipe()
    transientPhrase = phrase
  }

  private fun clearTransientPhrase() {
    transientPhrase?.wipe()
    transientPhrase = null
  }

  private fun clearPendingConfirmAnswers() {
    pendingConfirmAnswers?.values?.forEach { Arrays.fill(it, '\u0000') }
    pendingConfirmAnswers = null
  }

  private fun extractDisplayWords(phrase: MnemonicPhrase): List<String> =
    (0 until phrase.wordCount).map { idx ->
      val chars = phrase.wordAt(idx)
      try {
        String(chars)
      } finally {
        Arrays.fill(chars, '\u0000')
      }
    }

  override fun onCleared() {
    clearTransientPhrase()
    clearPendingConfirmAnswers()
    super.onCleared()
  }
}
