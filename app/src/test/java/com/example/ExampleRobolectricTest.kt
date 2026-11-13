package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeystoreCipherProvider
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.keystore.VaultWrongPinException
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.Network
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.LockedVaultScreen
import com.example.ui.screens.RecoveryPhraseScreen
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.screens.WelcomeCreateScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun read_app_name_from_context() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GramVault", appName)
  }

  @Test
  fun welcome_screen_without_system_lock_keeps_create_button_enabled() {
    composeTestRule.setContent {
      GramVaultTheme {
        WelcomeCreateScreen(
          isDeviceSecure = false,
          isBusy = false,
          errorMessage = null,
          onCreateWallet = {},
        )
      }
    }
    composeTestRule.onNodeWithTag("pin_fallback_info_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("welcome_create_wallet_button").assertIsEnabled()
  }

  @Test
  fun mainnet_home_renders_wallet_card_and_assets_without_testnet_banner() {
    val metadata =
      VaultMetadata(
        publicKeyHex = "00".repeat(32),
        mainnetRaw = "0:" + "00".repeat(32),
        testnetRaw = "0:" + "00".repeat(32),
        isBackedUp = false,
        createdAtEpochMs = 1_700_000_000_000L,
      )
    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = metadata,
          activeNetwork = Network.MAINNET,
          bannerMessage = null,
          onClearBanner = {},
          onSelectNetwork = {},
          onOpenBackup = {},
          onLockWallet = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("testnet_banner").assertDoesNotExist()
    composeTestRule.onNodeWithTag("backup_warning_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("wallet_hero_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("assets_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Gram").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Tether USD").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun testnet_mode_displays_testnet_banner() {
    val metadata =
      VaultMetadata(
        publicKeyHex = "00".repeat(32),
        mainnetRaw = "0:" + "00".repeat(32),
        testnetRaw = "0:" + "00".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
      )
    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = metadata,
          activeNetwork = Network.TESTNET,
          bannerMessage = null,
          onClearBanner = {},
          onSelectNetwork = {},
          onOpenBackup = {},
          onLockWallet = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("testnet_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("backup_warning_banner").assertDoesNotExist()
    composeTestRule.onNodeWithTag("backed_up_badge").assertIsDisplayed()
  }

  @Test
  fun recovery_phrase_and_locked_screens_render() {
    val sampleWords = List(12) { idx -> "word${idx + 1}" }
    composeTestRule.setContent {
      GramVaultTheme {
        RecoveryPhraseScreen(
          words = sampleWords,
          isAlreadyBackedUp = false,
          onBack = {},
          onProceedToVerify = {},
          onSkipBackup = {},
        )
      }
    }
    composeTestRule.onNodeWithTag("mnemonic_word_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mnemonic_word_12").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("proceed_backup_verify_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun locked_screen_renders_unlock_button() {
    composeTestRule.setContent {
      GramVaultTheme {
        LockedVaultScreen(
          isKeyInvalidated = false,
          isBusy = false,
          errorMessage = null,
          onUnlock = {},
          onResetVault = {},
        )
      }
    }
    composeTestRule.onNodeWithTag("unlock_wallet_button").assertIsDisplayed()
  }

  @Test
  fun vault_supports_device_keystore_auth_gating_and_restart_lock() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()

    val fakeAuth = FakeAuthGate(deviceSecure = true, nextResult = AuthResult.Authenticated)
    val fakeCipher = InMemoryAesCipherProvider()
    val keyStore = SecureKeyStore(context, fakeCipher)
    val metaStore = VaultMetadataStore(context)

    val core = WalletCoreImpl(context, fakeAuth, keyStore, metaStore, walletKitBridge = null)
    assertEquals(VaultState.Uninitialized, core.vaultState.value)

    fakeCipher.isAuthenticated = true
    val created = core.createWallet(Network.MAINNET).getOrThrow()
    val firstWords = (0 until created.mnemonic.wordCount).map { String(created.mnemonic.wordAt(it)) }
    assertEquals(12, firstWords.size)
    assertTrue(keyStore.hasVault())

    val rawCipherFile = File(context.filesDir, "vault/vault_seed.bin").readBytes()
    val cipherAscii = rawCipherFile.decodeToString()
    assertFalse(firstWords.any { cipherAscii.contains(it) })

    val challenge = core.createBackupChallenge()
    val answers = challenge.indicesOneBased.associateWith { i -> created.mnemonic.wordAt(i - 1) }
    assertTrue(core.confirmBackup(challenge, answers).getOrThrow())
    created.mnemonic.wipe()
    val unlockedState = core.vaultState.value as VaultState.Unlocked
    assertEquals(VaultSecurityMode.DEVICE_KEYSTORE, unlockedState.metadata.securityMode)
    assertTrue(unlockedState.metadata.isBackedUp)

    fakeCipher.isAuthenticated = false
    fakeAuth.nextResult = AuthResult.Cancelled
    val restartedCore = WalletCoreImpl(context, fakeAuth, keyStore, metaStore, walletKitBridge = null)
    assertTrue(restartedCore.vaultState.value is VaultState.Locked)
    assertTrue(restartedCore.unlockWallet(Network.MAINNET).isFailure)

    fakeAuth.nextResult = AuthResult.Authenticated
    fakeCipher.isAuthenticated = true
    val unlockedMeta = restartedCore.unlockWallet(Network.MAINNET).getOrThrow()
    assertEquals(created.publicKeyHex, unlockedMeta.publicKeyHex)
    assertEquals(created.mainnetAddress.raw, unlockedMeta.addressFor(Network.MAINNET).raw)
    assertEquals(created.testnetAddress.raw, unlockedMeta.addressFor(Network.TESTNET).raw)

    fakeCipher.keyInvalidated = true
    assertTrue(restartedCore.unlockWallet(Network.MAINNET).isFailure)
    assertTrue(restartedCore.vaultState.value is VaultState.KeyInvalidated)
  }

  @Test
  fun app_pin_fallback_encrypts_vault_and_enforces_pin_on_restart() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()

    val fakeAuth = FakeAuthGate(deviceSecure = false, nextResult = AuthResult.NoDeviceSecurity)
    val fakeCipher = InMemoryAesCipherProvider()
    val keyStore = SecureKeyStore(context, fakeCipher)
    val metaStore = VaultMetadataStore(context)

    val core = WalletCoreImpl(context, fakeAuth, keyStore, metaStore, walletKitBridge = null)
    assertTrue(core.createWallet(Network.MAINNET, appPin = null).isFailure)

    val created = core.createWallet(Network.MAINNET, appPin = "2468".toCharArray()).getOrThrow()
    val words = (0 until created.mnemonic.wordCount).map { String(created.mnemonic.wordAt(it)) }
    val rawCipherFile = File(context.filesDir, "vault/vault_seed.bin").readBytes().decodeToString()
    assertFalse(words.any { rawCipherFile.contains(it) })
    created.mnemonic.wipe()

    val restartedCore = WalletCoreImpl(context, fakeAuth, keyStore, metaStore, walletKitBridge = null)
    assertTrue(restartedCore.vaultState.value is VaultState.Locked)

    val wrongPinResult = restartedCore.unlockWallet(Network.MAINNET, appPin = "9999".toCharArray())
    assertTrue(wrongPinResult.exceptionOrNull() is VaultWrongPinException)
    assertTrue(restartedCore.vaultState.value is VaultState.Locked)

    val okMeta = restartedCore.unlockWallet(Network.MAINNET, appPin = "2468".toCharArray()).getOrThrow()
    assertEquals(VaultSecurityMode.APP_PIN_WITH_KEYSTORE, okMeta.securityMode)
    assertEquals(created.publicKeyHex, okMeta.publicKeyHex)
  }

  private class FakeAuthGate(
    var deviceSecure: Boolean,
    var nextResult: AuthResult,
  ) : AuthGate {
    override fun isDeviceSecure(): Boolean = deviceSecure

    override suspend fun authenticate(reason: AuthReason): AuthResult = nextResult
  }

  private class InMemoryAesCipherProvider : KeystoreCipherProvider {
    private var authKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var hwKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    var isAuthenticated: Boolean = true
    var keyInvalidated: Boolean = false

    override fun ensureKeyExists(requireUserAuth: Boolean) {
      if (requireUserAuth && authKeyBytes == null) {
        authKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
      if (!requireUserAuth && hwKeyBytes == null) {
        hwKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
    }

    override fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      if (requireUserAuth && !isAuthenticated) throw VaultAuthRequiredException()
      ensureKeyExists(requireUserAuth)
      val key = if (requireUserAuth) authKeyBytes else hwKeyBytes
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
      return AndroidKeyStoreCipherProvider.encodeEnvelope(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(envelope: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      if (requireUserAuth && !isAuthenticated) throw VaultAuthRequiredException()
      val key = (if (requireUserAuth) authKeyBytes else hwKeyBytes) ?: throw VaultKeyInvalidatedException()
      val (iv, ct) = AndroidKeyStoreCipherProvider.decodeEnvelope(envelope)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(ct)
    }

    override fun deleteKey(requireUserAuth: Boolean) {
      if (requireUserAuth) authKeyBytes = null else hwKeyBytes = null
    }
  }
}
