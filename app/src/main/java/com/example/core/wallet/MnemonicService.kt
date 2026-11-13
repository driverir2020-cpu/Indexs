package com.example.core.wallet

import com.example.core.model.BackupChallenge
import com.example.core.model.MnemonicPhrase
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.ton.kotlin.crypto.PrivateKeyEd25519

class DerivedEd25519Key(
  seed32: ByteArray,
  publicKey32: ByteArray,
) : AutoCloseable {
  val seed32: ByteArray = seed32.copyOf()
  val publicKey32: ByteArray = publicKey32.copyOf()

  fun wipe() {
    Arrays.fill(seed32, 0.toByte())
  }

  override fun close() {
    wipe()
  }
}

object MnemonicService {
  private val BIP39_SALT_PREFIX = "mnemonic".toByteArray(Charsets.UTF_8)
  private val SLIP10_ED25519_KEY = "ed25519 seed".toByteArray(Charsets.US_ASCII)
  private val TON_SLIP10_PATH =
    intArrayOf(
      0x8000002C.toInt(), // 44'
      0x8000025F.toInt(), // 607'
      0x80000000.toInt(), // 0'
    )

  fun generate12WordMnemonic(random: SecureRandom = SecureRandom()): MnemonicPhrase {
    val entropy = ByteArray(16)
    val digest = MessageDigest.getInstance("SHA-256")
    var hash: ByteArray? = null
    return try {
      random.nextBytes(entropy)
      hash = digest.digest(entropy)
      val checksumBits = (hash[0].toInt() and 0xF0) ushr 4 // 4 bits for 128-bit entropy
      val words = ArrayList<CharArray>(12)
      var bitBuffer = 0
      var bitCount = 0
      for (b in entropy) {
        bitBuffer = (bitBuffer shl 8) or (b.toInt() and 0xFF)
        bitCount += 8
        while (bitCount >= 11) {
          bitCount -= 11
          val index = (bitBuffer ushr bitCount) and 0x7FF
          words.add(Bip39Wordlist.WORDS[index].toCharArray())
        }
      }
      bitBuffer = (bitBuffer shl 4) or checksumBits
      bitCount += 4
      val lastIndex = bitBuffer and 0x7FF
      require(bitCount == 11)
      words.add(Bip39Wordlist.WORDS[lastIndex].toCharArray())
      MnemonicPhrase(words).also { words.forEach { Arrays.fill(it, '\u0000') } }
    } finally {
      Arrays.fill(entropy, 0.toByte())
      hash?.let { Arrays.fill(it, 0.toByte()) }
    }
  }

  fun validateBip39Mnemonic(mnemonic: MnemonicPhrase): Boolean {
    val count = mnemonic.wordCount
    if (count != 12 && count != 24) return false
    val totalBits = count * 11
    val checksumBits = count / 3 // 4 for 12 words, 8 for 24 words
    val entropyBytesCount = (totalBits - checksumBits) / 8
    val entropy = ByteArray(entropyBytesCount)
    var hash: ByteArray? = null
    return try {
      var bitBuffer = 0
      var bitCount = 0
      var bytePos = 0
      var parsedChecksum = 0
      for (i in 0 until count) {
        val rawWord = mnemonic.wordAt(i)
        val wordIdx =
          try {
            val normalized = Normalizer.normalize(CharBuffer.wrap(rawWord), Normalizer.Form.NFKD).trim().lowercase()
            Bip39Wordlist.indexOf(normalized)
          } finally {
            Arrays.fill(rawWord, '\u0000')
          }
        if (wordIdx < 0) return false
        bitBuffer = (bitBuffer shl 11) or wordIdx
        bitCount += 11
        while (bitCount >= 8 && bytePos < entropyBytesCount) {
          bitCount -= 8
          entropy[bytePos++] = ((bitBuffer ushr bitCount) and 0xFF).toByte()
        }
      }
      parsedChecksum = bitBuffer and ((1 shl checksumBits) - 1)
      hash = MessageDigest.getInstance("SHA-256").digest(entropy)
      val expectedChecksum =
        if (checksumBits == 4) {
          (hash[0].toInt() and 0xF0) ushr 4
        } else {
          hash[0].toInt() and 0xFF
        }
      parsedChecksum == expectedChecksum
    } finally {
      Arrays.fill(entropy, 0.toByte())
      hash?.let { Arrays.fill(it, 0.toByte()) }
    }
  }

  fun deriveMultichainKey(mnemonic: MnemonicPhrase): DerivedEd25519Key {
    require(validateBip39Mnemonic(mnemonic)) { "Invalid BIP-39 mnemonic checksum or word" }
    val mnemonicChars = mnemonic.toSpaceSeparatedCharArray()
    val spec = PBEKeySpec(mnemonicChars, BIP39_SALT_PREFIX, 2048, 512)
    var bip39Seed: ByteArray? = null
    var nodeKey: ByteArray? = null
    var chainCode: ByteArray? = null
    var hmacResult: ByteArray? = null
    val hardenedData = ByteArray(37)
    return try {
      val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
      bip39Seed = skf.generateSecret(spec).encoded
      hmacResult = hmacSha512(SLIP10_ED25519_KEY, bip39Seed)
      nodeKey = hmacResult.copyOfRange(0, 32)
      chainCode = hmacResult.copyOfRange(32, 64)
      Arrays.fill(hmacResult, 0.toByte())

      for (index in TON_SLIP10_PATH) {
        hardenedData[0] = 0x00
        System.arraycopy(nodeKey!!, 0, hardenedData, 1, 32)
        hardenedData[33] = (index ushr 24).toByte()
        hardenedData[34] = (index ushr 16).toByte()
        hardenedData[35] = (index ushr 8).toByte()
        hardenedData[36] = index.toByte()

        hmacResult = hmacSha512(chainCode!!, hardenedData)
        Arrays.fill(nodeKey, 0.toByte())
        Arrays.fill(chainCode, 0.toByte())
        nodeKey = hmacResult.copyOfRange(0, 32)
        chainCode = hmacResult.copyOfRange(32, 64)
        Arrays.fill(hmacResult, 0.toByte())
      }

      val seed32 = nodeKey!!
      val publicKey32 = PrivateKeyEd25519(seed32).publicKey().key.toByteArray()
      DerivedEd25519Key(seed32 = seed32, publicKey32 = publicKey32)
    } finally {
      Arrays.fill(mnemonicChars, '\u0000')
      spec.clearPassword()
      bip39Seed?.let { Arrays.fill(it, 0.toByte()) }
      nodeKey?.let { Arrays.fill(it, 0.toByte()) }
      chainCode?.let { Arrays.fill(it, 0.toByte()) }
      hmacResult?.let { Arrays.fill(it, 0.toByte()) }
      Arrays.fill(hardenedData, 0.toByte())
    }
  }

  fun decodePhraseFromUtf8(utf8Bytes: ByteArray): MnemonicPhrase {
    val charBuffer = Charsets.UTF_8.decode(ByteBuffer.wrap(utf8Bytes))
    val chars = CharArray(charBuffer.remaining())
    charBuffer.get(chars)
    return try {
      parseSpaceSeparatedChars(chars)
    } finally {
      Arrays.fill(chars, '\u0000')
      if (charBuffer.hasArray()) Arrays.fill(charBuffer.array(), '\u0000')
    }
  }

  fun encodePhraseToUtf8(mnemonic: MnemonicPhrase): ByteArray {
    val chars = mnemonic.toSpaceSeparatedCharArray()
    val byteBuf = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
    return try {
      ByteArray(byteBuf.remaining()).also { byteBuf.get(it) }
    } finally {
      Arrays.fill(chars, '\u0000')
      if (byteBuf.hasArray()) Arrays.fill(byteBuf.array(), 0.toByte())
    }
  }

  fun parseSpaceSeparatedChars(chars: CharArray): MnemonicPhrase {
    val words = mutableListOf<CharArray>()
    var start = -1
    for (i in chars.indices) {
      val isWs = chars[i].isWhitespace()
      if (!isWs && start == -1) {
        start = i
      } else if (isWs && start != -1) {
        words.add(chars.copyOfRange(start, i))
        start = -1
      }
    }
    if (start != -1) {
      words.add(chars.copyOfRange(start, chars.size))
    }
    return MnemonicPhrase(words).also { words.forEach { w -> Arrays.fill(w, '\u0000') } }
  }

  fun createBackupChallenge(
    wordCount: Int = 12,
    sampleCount: Int = 3,
    random: SecureRandom = SecureRandom(),
  ): BackupChallenge {
    require(sampleCount in 1..wordCount)
    val pool = (1..wordCount).toMutableList()
    val chosen = mutableListOf<Int>()
    repeat(sampleCount) {
      val pickIdx = random.nextInt(pool.size)
      chosen.add(pool.removeAt(pickIdx))
    }
    return BackupChallenge(chosen.sorted())
  }

  fun verifyBackupChallenge(
    mnemonic: MnemonicPhrase,
    challenge: BackupChallenge,
    userAnswers: Map<Int, CharArray>,
  ): Boolean {
    if (challenge.indicesOneBased.isEmpty()) return false
    return challenge.indicesOneBased.all { oneBased ->
      val indexZero = oneBased - 1
      if (indexZero !in 0 until mnemonic.wordCount) return@all false
      val expected = mnemonic.wordAt(indexZero)
      val supplied = userAnswers[oneBased] ?: return@all false.also { Arrays.fill(expected, '\u0000') }
      try {
        constantTimeNormalizedEquals(expected, supplied)
      } finally {
        Arrays.fill(expected, '\u0000')
      }
    }
  }

  private fun constantTimeNormalizedEquals(expected: CharArray, supplied: CharArray): Boolean {
    var start = 0
    var end = supplied.size
    while (start < end && supplied[start].isWhitespace()) start++
    while (end > start && supplied[end - 1].isWhitespace()) end--
    val trimmedLen = end - start
    if (trimmedLen != expected.size) return false
    var diff = 0
    for (i in expected.indices) {
      diff = diff or (expected[i].code xor supplied[start + i].lowercaseChar().code)
    }
    return diff == 0
  }

  private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(key, "HmacSHA512"))
    return mac.doFinal(data)
  }
}
