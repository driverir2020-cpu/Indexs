package com.example.core.wallet

import android.content.Context
import com.example.core.keystore.AuthGate
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.keystore.VaultWrongPinException
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.BackupChallenge
import com.example.core.model.MnemonicPhrase
import com.example.core.model.Network
import com.example.core.model.NewWallet
import com.example.core.model.TonAddress
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import java.util.Arrays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface WalletCore {
  val vaultState: StateFlow<VaultState>

  fun isDeviceSecure(): Boolean

  fun activeSecurityMode(): VaultSecurityMode

  suspend fun createWallet(
    initialNetwork: Network = Network.MAINNET,
    appPin: CharArray? = null,
  ): Result<NewWallet>

  suspend fun unlockWallet(
    network: Network = Network.MAINNET,
    appPin: CharArray? = null,
  ): Result<VaultMetadata>

  suspend fun revealRecoveryPhrase(appPin: CharArray? = null): Result<MnemonicPhrase>

  fun createBackupChallenge(): BackupChallenge

  suspend fun confirmBackup(
    challenge: BackupChallenge,
    answers: Map<Int, CharArray>,
    appPin: CharArray? = null,
  ): Result<Boolean>

  suspend fun switchNetwork(network: Network)

  fun lockWallet()

  fun resetVault()

  fun getReceiveAddress(network: Network? = null): TonAddress?
}

class WalletCoreImpl(
  applicationContext: Context,
  private val authGate: AuthGate,
  private val secureKeyStore: SecureKeyStore = SecureKeyStore(applicationContext),
  private val metadataStore: VaultMetadataStore = VaultMetadataStore(applicationContext),
  private val walletKitBridge: WalletKitBridge? = WalletKitBridge(applicationContext, secureKeyStore),
) : WalletCore {

  private val _vaultState = MutableStateFlow(loadInitialState())
  override val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

  private fun loadInitialState(): VaultState {
    val meta = metadataStore.load()
    return if (meta != null && secureKeyStore.hasVault()) {
      VaultState.Locked(meta)
    } else {
      if (meta != null && !secureKeyStore.hasVault()) {
        metadataStore.clear()
      }
      VaultState.Uninitialized
    }
  }

  override fun isDeviceSecure(): Boolean = authGate.isDeviceSecure()

  override fun activeSecurityMode(): VaultSecurityMode {
    val existing = metadataStore.load()?.securityMode
    if (existing != null) return existing
    return if (authGate.isDeviceSecure()) VaultSecurityMode.DEVICE_KEYSTORE
    else VaultSecurityMode.APP_PIN_WITH_KEYSTORE
  }

  override suspend fun createWallet(initialNetwork: Network, appPin: CharArray?): Result<NewWallet> {
    val deviceSecure = authGate.isDeviceSecure()
    val targetMode =
      if (deviceSecure) VaultSecurityMode.DEVICE_KEYSTORE
      else VaultSecurityMode.APP_PIN_WITH_KEYSTORE

    if (deviceSecure) {
      when (val auth = authGate.authenticate(AuthReason.CREATE_WALLET)) {
        is AuthResult.Authenticated -> Unit
        is AuthResult.Cancelled -> {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Authentication cancelled"))
        }
        is AuthResult.NoDeviceSecurity ->
          return Result.failure(VaultException("Device lock screen is required"))
        is AuthResult.Failed -> {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException(auth.message))
        }
      }
    } else {
      if (appPin == null || appPin.size < 4) {
        appPin?.let { Arrays.fill(it, '\u0000') }
        return Result.failure(VaultException("Set a 4 to 8 digit App PIN to protect your wallet"))
      }
    }

    return runCatching {
      val mnemonic = withContext(Dispatchers.Default) { MnemonicService.generate12WordMnemonic() }
      val derived = withContext(Dispatchers.Default) { MnemonicService.deriveMultichainKey(mnemonic) }
      val mnemonicUtf8 = MnemonicService.encodePhraseToUtf8(mnemonic)
      try {
        val mainnetAddr = AddressService.deriveV5R1Address(derived.publicKey32, Network.MAINNET)
        val testnetAddr = AddressService.deriveV5R1Address(derived.publicKey32, Network.TESTNET)
        val pubHex = AddressService.bytesToHex(derived.publicKey32)

        withContext(Dispatchers.IO) {
          if (targetMode == VaultSecurityMode.DEVICE_KEYSTORE) {
            secureKeyStore.saveWithDeviceAuth(derived.seed32, mnemonicUtf8)
          } else {
            secureKeyStore.saveWithAppPin(derived.seed32, mnemonicUtf8, appPin!!)
          }
        }
        val meta =
          VaultMetadata(
            publicKeyHex = pubHex,
            mainnetRaw = mainnetAddr.raw,
            testnetRaw = testnetAddr.raw,
            isBackedUp = false,
            createdAtEpochMs = System.currentTimeMillis(),
            securityMode = targetMode,
          )
        withContext(Dispatchers.IO) { metadataStore.save(meta) }

        val synced = syncWalletKit(meta, initialNetwork)
        _vaultState.value =
          VaultState.Unlocked(
            metadata = meta,
            activeNetwork = initialNetwork,
            walletKitSynced = synced,
          )
        NewWallet(
          mainnetAddress = mainnetAddr,
          testnetAddress = testnetAddr,
          publicKeyHex = pubHex,
          mnemonic = mnemonic,
        )
      } catch (t: Throwable) {
        mnemonic.wipe()
        throw t
      } finally {
        derived.wipe()
        Arrays.fill(mnemonicUtf8, 0.toByte())
        appPin?.let { Arrays.fill(it, '\u0000') }
      }
    }
  }

  override suspend fun unlockWallet(network: Network, appPin: CharArray?): Result<VaultMetadata> {
    val currentMeta = metadataStore.load()
    if (currentMeta == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("No wallet exists on this device"))
    }

    val useAppPin = currentMeta.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    if (!useAppPin) {
      when (val auth = authGate.authenticate(AuthReason.UNLOCK_WALLET)) {
        is AuthResult.Authenticated -> Unit
        is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
        is AuthResult.NoDeviceSecurity ->
          return Result.failure(VaultException("Device lock screen is required"))
        is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
      }
    } else if (appPin == null || appPin.size < 4) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultAuthRequiredException())
    }

    return try {
      val derivedPubHex =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedSeed(pin = appPin, useAppPinMode = useAppPin) { seed32 ->
            val pub = org.ton.kotlin.crypto.PrivateKeyEd25519(seed32).publicKey().key.toByteArray()
            AddressService.bytesToHex(pub)
          }
        }
      require(derivedPubHex == currentMeta.publicKeyHex) { "Key mismatch detected in vault" }
      val synced = syncWalletKit(currentMeta, network)
      _vaultState.value =
        VaultState.Unlocked(
          metadata = currentMeta,
          activeNetwork = network,
          walletKitSynced = synced,
        )
      Result.success(currentMeta)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(currentMeta)
      Result.failure(e)
    } catch (e: VaultWrongPinException) {
      Result.failure(e)
    } catch (e: VaultAuthRequiredException) {
      _vaultState.value = VaultState.Locked(currentMeta)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun revealRecoveryPhrase(appPin: CharArray?): Result<MnemonicPhrase> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE

    if (!useAppPin) {
      when (val auth = authGate.authenticate(AuthReason.VIEW_RECOVERY_PHRASE)) {
        is AuthResult.Authenticated -> Unit
        is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
        is AuthResult.NoDeviceSecurity ->
          return Result.failure(VaultException("Device lock screen is required"))
        is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
      }
    } else if (appPin == null || appPin.size < 4) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultAuthRequiredException())
    }

    return try {
      val phrase =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedMnemonic(pin = appPin, useAppPinMode = useAppPin) { utf8 ->
            MnemonicService.decodePhraseFromUtf8(utf8)
          }
        }
      Result.success(phrase)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override fun createBackupChallenge(): BackupChallenge =
    MnemonicService.createBackupChallenge(wordCount = 12, sampleCount = 3)

  override suspend fun confirmBackup(
    challenge: BackupChallenge,
    answers: Map<Int, CharArray>,
    appPin: CharArray?,
  ): Result<Boolean> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      answers.values.forEach { a -> Arrays.fill(a, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE

    return try {
      val passed =
        try {
          withContext(Dispatchers.IO) {
            secureKeyStore.withDecryptedMnemonic(pin = appPin, useAppPinMode = useAppPin) { utf8 ->
              val phrase = MnemonicService.decodePhraseFromUtf8(utf8)
              try {
                MnemonicService.verifyBackupChallenge(phrase, challenge, answers)
              } finally {
                phrase.wipe()
              }
            }
          }
        } catch (e: VaultAuthRequiredException) {
          if (useAppPin) throw e
          when (val auth = authGate.authenticate(AuthReason.VIEW_RECOVERY_PHRASE)) {
            is AuthResult.Authenticated -> {
              withContext(Dispatchers.IO) {
                secureKeyStore.withDecryptedMnemonic(pin = null, useAppPinMode = false) { utf8 ->
                  val phrase = MnemonicService.decodePhraseFromUtf8(utf8)
                  try {
                    MnemonicService.verifyBackupChallenge(phrase, challenge, answers)
                  } finally {
                    phrase.wipe()
                  }
                }
              }
            }
            is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
            is AuthResult.NoDeviceSecurity ->
              return Result.failure(VaultException("Device lock screen is required"))
            is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
          }
        }

      if (passed) {
        val updatedMeta = current.metadata.copy(isBackedUp = true)
        withContext(Dispatchers.IO) { metadataStore.save(updatedMeta) }
        _vaultState.value = current.copy(metadata = updatedMeta)
      }
      Result.success(passed)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      answers.values.forEach { Arrays.fill(it, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun switchNetwork(network: Network) {
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    if (current.activeNetwork == network && current.walletKitSynced) return
    val synced = syncWalletKit(current.metadata, network)
    _vaultState.value = current.copy(activeNetwork = network, walletKitSynced = synced)
  }

  override fun lockWallet() {
    secureKeyStore.clearPinSession()
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    _vaultState.value = VaultState.Locked(current.metadata)
  }

  override fun resetVault() {
    secureKeyStore.clearVault()
    metadataStore.clear()
    _vaultState.value = VaultState.Uninitialized
  }

  override fun getReceiveAddress(network: Network?): TonAddress? {
    val current = _vaultState.value as? VaultState.Unlocked ?: return null
    val targetNetwork = network ?: current.activeNetwork
    return current.metadata.addressFor(targetNetwork)
  }

  private suspend fun syncWalletKit(metadata: VaultMetadata, network: Network): Boolean {
    val bridge = walletKitBridge ?: return false
    val useAppPin = metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    return runCatching {
        val kitAddr = bridge.attachV5R1Wallet(metadata.publicKeyHex, network, useAppPinMode = useAppPin)
        val expectedAddr = metadata.addressFor(network)
        kitAddr.raw == expectedAddr.raw
      }
      .getOrDefault(false)
  }
}
