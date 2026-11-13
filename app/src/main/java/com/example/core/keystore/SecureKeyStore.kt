package com.example.core.keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultKeyInvalidatedException(cause: Throwable? = null) :
  Exception("Hardware encryption key was invalidated after device security settings changed", cause)

class VaultAuthRequiredException(cause: Throwable? = null) :
  Exception("Recent biometric or device credential authentication is required", cause)

class VaultWrongPinException(cause: Throwable? = null) :
  Exception("Incorrect wallet PIN", cause)

class VaultNotFoundException : Exception("No encrypted vault stored on device")

interface KeystoreCipherProvider {
  fun ensureKeyExists(requireUserAuth: Boolean = true)
  fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean = true): ByteArray
  fun decrypt(envelope: ByteArray, requireUserAuth: Boolean = true): ByteArray
  fun deleteKey(requireUserAuth: Boolean = true)
}

class AndroidKeyStoreCipherProvider(
  private val authKeyAlias: String = DEFAULT_KEY_ALIAS,
  private val hwKeyAlias: String = DEFAULT_HW_KEY_ALIAS,
  private val authValiditySeconds: Int = DEFAULT_AUTH_VALIDITY_SECONDS,
) : KeystoreCipherProvider {

  private fun aliasFor(requireUserAuth: Boolean): String =
    if (requireUserAuth) authKeyAlias else hwKeyAlias

  override fun ensureKeyExists(requireUserAuth: Boolean) {
    val alias = aliasFor(requireUserAuth)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(alias)) return
    generateKey(alias, requireUserAuth)
  }

  override fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean): ByteArray {
    ensureKeyExists(requireUserAuth)
    val key = loadSecretKey(aliasFor(requireUserAuth))
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val ciphertext = cipher.doFinal(plaintext)
      encodeEnvelope(iv, ciphertext)
    } catch (e: KeyPermanentlyInvalidatedException) {
      throw VaultKeyInvalidatedException(e)
    } catch (e: UserNotAuthenticatedException) {
      throw VaultAuthRequiredException(e)
    }
  }

  override fun decrypt(envelope: ByteArray, requireUserAuth: Boolean): ByteArray {
    val key = loadSecretKey(aliasFor(requireUserAuth))
    val (iv, ciphertext) = decodeEnvelope(envelope)
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(ciphertext)
    } catch (e: KeyPermanentlyInvalidatedException) {
      throw VaultKeyInvalidatedException(e)
    } catch (e: UserNotAuthenticatedException) {
      throw VaultAuthRequiredException(e)
    }
  }

  override fun deleteKey(requireUserAuth: Boolean) {
    val alias = aliasFor(requireUserAuth)
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(alias)) {
      keyStore.deleteEntry(alias)
    }
  }

  private fun loadSecretKey(alias: String): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    return (keyStore.getKey(alias, null) as? SecretKey) ?: throw VaultKeyInvalidatedException()
  }

  private fun generateKey(alias: String, requireUserAuth: Boolean) {
    val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val builder =
      KeyGenParameterSpec.Builder(
          alias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)

    if (requireUserAuth) {
      builder.setUserAuthenticationRequired(true)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setUserAuthenticationParameters(
          authValiditySeconds,
          KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
        )
      } else {
        @Suppress("DEPRECATION")
        builder.setUserAuthenticationValidityDurationSeconds(authValiditySeconds)
      }
    }
    keyGen.init(builder.build())
    keyGen.generateKey()
  }

  companion object {
    const val DEFAULT_KEY_ALIAS = "gramvault_master_aes_v1"
    const val DEFAULT_HW_KEY_ALIAS = "gramvault_pin_hw_v1"
    const val DEFAULT_AUTH_VALIDITY_SECONDS = 30
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val ENVELOPE_VERSION: Byte = 1

    fun encodeEnvelope(iv: ByteArray, ciphertext: ByteArray): ByteArray {
      require(iv.size in 12..16) { "Unexpected GCM IV length: ${iv.size}" }
      return ByteArray(2 + iv.size + ciphertext.size).apply {
        this[0] = ENVELOPE_VERSION
        this[1] = iv.size.toByte()
        System.arraycopy(iv, 0, this, 2, iv.size)
        System.arraycopy(ciphertext, 0, this, 2 + iv.size, ciphertext.size)
      }
    }

    fun decodeEnvelope(envelope: ByteArray): Pair<ByteArray, ByteArray> {
      require(envelope.size > 16) { "Encrypted envelope is truncated" }
      require(envelope[0] == ENVELOPE_VERSION) { "Unsupported envelope version: ${envelope[0]}" }
      val ivLen = envelope[1].toInt() and 0xFF
      require(ivLen in 12..16 && envelope.size > 2 + ivLen) { "Invalid IV length: $ivLen" }
      val iv = envelope.copyOfRange(2, 2 + ivLen)
      val ciphertext = envelope.copyOfRange(2 + ivLen, envelope.size)
      return iv to ciphertext
    }
  }
}

class SecureKeyStore(
  context: Context,
  private val cipherProvider: KeystoreCipherProvider = AndroidKeyStoreCipherProvider(),
) {
  private val vaultDir = File(context.filesDir, "vault")
  private val seedFile = File(vaultDir, "vault_seed.bin")
  private val random = SecureRandom()

  // Short-lived in-memory wrapping key for active PIN session (zeroed on lock or expiry)
  private var activePinSessionKey: ByteArray? = null
  private var activePinSessionExpiresAtMs: Long = 0L

  fun hasVault(): Boolean = seedFile.exists() && seedFile.length() > 0L

  fun saveWithDeviceAuth(seed32: ByteArray, mnemonicUtf8: ByteArray) {
    val plaintext = encodePlaintext(seed32, mnemonicUtf8)
    try {
      val envelope = cipherProvider.encrypt(plaintext, requireUserAuth = true)
      writeAtomic(envelope)
    } finally {
      Arrays.fill(plaintext, 0.toByte())
    }
  }

  fun saveWithAppPin(seed32: ByteArray, mnemonicUtf8: ByteArray, pin: CharArray) {
    require(pin.size >= 4) { "App PIN must be at least 4 digits" }
    val plaintext = encodePlaintext(seed32, mnemonicUtf8)
    val salt = ByteArray(PIN_SALT_BYTES).also { random.nextBytes(it) }
    val pinKey = derivePinAesKey(pin, salt)
    var innerLayer: ByteArray? = null
    try {
      val innerCipher = Cipher.getInstance(AES_GCM)
      innerCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pinKey, "AES"))
      val innerIv = innerCipher.iv
      val innerCiphertext = innerCipher.doFinal(plaintext)
      innerLayer = encodePinEnvelope(salt, innerIv, innerCiphertext)
      val outerEnvelope = cipherProvider.encrypt(innerLayer, requireUserAuth = false)
      writeAtomic(outerEnvelope)
      cachePinSessionKey(pinKey)
    } finally {
      Arrays.fill(plaintext, 0.toByte())
      Arrays.fill(salt, 0.toByte())
      Arrays.fill(pinKey, 0.toByte())
      innerLayer?.let { Arrays.fill(it, 0.toByte()) }
    }
  }

  fun <T> withDecryptedSeed(pin: CharArray? = null, useAppPinMode: Boolean = false, block: (seed32: ByteArray) -> T): T {
    val plaintext = decryptVaultPlaintext(pin, useAppPinMode)
    val seed32 = extractSeed32(plaintext)
    return try {
      block(seed32)
    } finally {
      Arrays.fill(seed32, 0.toByte())
      Arrays.fill(plaintext, 0.toByte())
    }
  }

  fun <T> withDecryptedMnemonic(pin: CharArray? = null, useAppPinMode: Boolean = false, block: (mnemonicUtf8: ByteArray) -> T): T {
    val plaintext = decryptVaultPlaintext(pin, useAppPinMode)
    val mnemonicBytes = extractMnemonicBytes(plaintext)
    return try {
      block(mnemonicBytes)
    } finally {
      Arrays.fill(mnemonicBytes, 0.toByte())
      Arrays.fill(plaintext, 0.toByte())
    }
  }

  fun clearPinSession() {
    activePinSessionKey?.let { Arrays.fill(it, 0.toByte()) }
    activePinSessionKey = null
    activePinSessionExpiresAtMs = 0L
  }

  fun clearVault() {
    clearPinSession()
    if (seedFile.exists()) {
      runCatching {
        val len = seedFile.length().toInt().coerceAtLeast(64)
        seedFile.writeBytes(ByteArray(len))
      }
      seedFile.delete()
    }
    cipherProvider.deleteKey(requireUserAuth = true)
    cipherProvider.deleteKey(requireUserAuth = false)
  }

  private fun decryptVaultPlaintext(pin: CharArray?, useAppPinMode: Boolean): ByteArray {
    if (!hasVault()) throw VaultNotFoundException()
    val envelope = seedFile.readBytes()
    if (!useAppPinMode) {
      return cipherProvider.decrypt(envelope, requireUserAuth = true)
    }

    val outerPlain = cipherProvider.decrypt(envelope, requireUserAuth = false)
    return try {
      val (salt, innerIv, innerCiphertext) = decodePinEnvelope(outerPlain)
      val derivedKey: ByteArray? =
        if (pin != null) {
          derivePinAesKey(pin, salt)
        } else {
          getCachedPinSessionKey()
        }
      if (derivedKey == null) throw VaultAuthRequiredException()
      try {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(128, innerIv))
        val result = cipher.doFinal(innerCiphertext)
        cachePinSessionKey(derivedKey)
        result
      } catch (e: GeneralSecurityException) {
        if (pin != null) throw VaultWrongPinException(e) else throw VaultAuthRequiredException(e)
      } finally {
        Arrays.fill(derivedKey, 0.toByte())
      }
    } finally {
      Arrays.fill(outerPlain, 0.toByte())
    }
  }

  private fun cachePinSessionKey(keyBytes: ByteArray) {
    clearPinSession()
    activePinSessionKey = keyBytes.copyOf()
    activePinSessionExpiresAtMs = System.currentTimeMillis() + PIN_SESSION_WINDOW_MS
  }

  private fun getCachedPinSessionKey(): ByteArray? {
    val cached = activePinSessionKey ?: return null
    if (System.currentTimeMillis() > activePinSessionExpiresAtMs) {
      clearPinSession()
      return null
    }
    return cached.copyOf()
  }

  private fun encodePlaintext(seed32: ByteArray, mnemonicUtf8: ByteArray): ByteArray {
    require(seed32.size == 32) { "Ed25519 seed must be 32 bytes" }
    require(mnemonicUtf8.size <= 4096) { "Mnemonic payload too large" }
    val plaintext = ByteArray(MAGIC.size + 1 + seed32.size + 2 + mnemonicUtf8.size)
    var pos = 0
    System.arraycopy(MAGIC, 0, plaintext, pos, MAGIC.size)
    pos += MAGIC.size
    plaintext[pos++] = seed32.size.toByte()
    System.arraycopy(seed32, 0, plaintext, pos, seed32.size)
    pos += seed32.size
    val mLenBytes = ByteBuffer.allocate(2).putShort(mnemonicUtf8.size.toShort()).array()
    plaintext[pos++] = mLenBytes[0]
    plaintext[pos++] = mLenBytes[1]
    System.arraycopy(mnemonicUtf8, 0, plaintext, pos, mnemonicUtf8.size)
    return plaintext
  }

  private fun writeAtomic(bytes: ByteArray) {
    if (!vaultDir.exists()) vaultDir.mkdirs()
    val tempFile = File(vaultDir, "vault_seed.bin.tmp")
    tempFile.writeBytes(bytes)
    if (!tempFile.renameTo(seedFile)) {
      seedFile.writeBytes(bytes)
      tempFile.delete()
    }
  }

  private fun extractSeed32(plaintext: ByteArray): ByteArray {
    validateMagic(plaintext)
    val seedLen = plaintext[MAGIC.size].toInt() and 0xFF
    require(seedLen == 32 && plaintext.size >= MAGIC.size + 1 + seedLen + 2) {
      "Corrupted vault payload"
    }
    return plaintext.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + seedLen)
  }

  private fun extractMnemonicBytes(plaintext: ByteArray): ByteArray {
    validateMagic(plaintext)
    val seedLen = plaintext[MAGIC.size].toInt() and 0xFF
    val lenOffset = MAGIC.size + 1 + seedLen
    require(plaintext.size >= lenOffset + 2) { "Corrupted vault mnemonic header" }
    val mnemonicLen =
      ((plaintext[lenOffset].toInt() and 0xFF) shl 8) or (plaintext[lenOffset + 1].toInt() and 0xFF)
    val start = lenOffset + 2
    require(plaintext.size == start + mnemonicLen) { "Corrupted vault mnemonic length" }
    return plaintext.copyOfRange(start, start + mnemonicLen)
  }

  private fun validateMagic(plaintext: ByteArray) {
    require(
      plaintext.size >= MAGIC.size + 3 &&
        plaintext[0] == MAGIC[0] &&
        plaintext[1] == MAGIC[1] &&
        plaintext[2] == MAGIC[2]
    ) {
      "Invalid vault payload header"
    }
  }

  private fun derivePinAesKey(pin: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin, salt, PIN_KDF_ITERATIONS, 256)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun encodePinEnvelope(salt: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
    return ByteArray(3 + salt.size + iv.size + ciphertext.size).apply {
      this[0] = PIN_ENVELOPE_VERSION
      this[1] = salt.size.toByte()
      System.arraycopy(salt, 0, this, 2, salt.size)
      val ivLenIdx = 2 + salt.size
      this[ivLenIdx] = iv.size.toByte()
      System.arraycopy(iv, 0, this, ivLenIdx + 1, iv.size)
      System.arraycopy(ciphertext, 0, this, ivLenIdx + 1 + iv.size, ciphertext.size)
    }
  }

  private fun decodePinEnvelope(data: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
    require(data.size > 32 && data[0] == PIN_ENVELOPE_VERSION) { "Corrupted PIN vault envelope" }
    val saltLen = data[1].toInt() and 0xFF
    val ivLenIdx = 2 + saltLen
    require(saltLen == PIN_SALT_BYTES && data.size > ivLenIdx + 16)
    val ivLen = data[ivLenIdx].toInt() and 0xFF
    require(ivLen in 12..16 && data.size > ivLenIdx + 1 + ivLen)
    val salt = data.copyOfRange(2, ivLenIdx)
    val iv = data.copyOfRange(ivLenIdx + 1, ivLenIdx + 1 + ivLen)
    val ct = data.copyOfRange(ivLenIdx + 1 + ivLen, data.size)
    return Triple(salt, iv, ct)
  }

  companion object {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val PIN_ENVELOPE_VERSION: Byte = 2
    private const val PIN_SALT_BYTES = 16
    private const val PIN_KDF_ITERATIONS = 100_000
    private const val PIN_SESSION_WINDOW_MS = 30_000L
  }
}
