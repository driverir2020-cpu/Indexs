package com.example

import com.example.core.model.MnemonicPhrase
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.wallet.AddressService
import com.example.core.wallet.Bip39Wordlist
import com.example.core.wallet.MnemonicService
import com.example.ui.components.encodeQrMatrix
import io.ton.walletkit.api.MAINNET
import io.ton.walletkit.api.TESTNET
import io.ton.walletkit.api.generated.TONNetwork
import io.ton.walletkit.api.isMainnet
import io.ton.walletkit.api.isTestnet
import io.ton.walletkit.model.TONRawAddress
import io.ton.walletkit.model.TONUserFriendlyAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun network_separation_holds() {
    assertNotEquals(Network.MAINNET, Network.TESTNET)
    assertNotEquals(Network.MAINNET.v5r1WalletId, Network.TESTNET.v5r1WalletId)
    assertEquals(0x7FFFFF11L, Network.MAINNET.v5r1WalletId)
    assertEquals(0x7FFFFFFDL, Network.TESTNET.v5r1WalletId)
    assertNotEquals(TONNetwork.MAINNET.chainId, TONNetwork.TESTNET.chainId)
    assertTrue(TONNetwork.TESTNET.isTestnet)
    assertFalse(TONNetwork.TESTNET.isMainnet)
    assertTrue(TONNetwork.MAINNET.isMainnet)
  }

  @Test
  fun walletkit_address_resolution_and_wrong_network_rejection() {
    val rawHex = "0:0000000000000000000000000000000000000000000000000000000000000000"
    val raw = TONRawAddress(rawHex)
    val testnetFriendly = TONUserFriendlyAddress(raw, isBounceable = false, isTestnetOnly = true)
    val mainnetFriendly = TONUserFriendlyAddress(raw, isBounceable = false, isTestnetOnly = false)
    assertTrue(testnetFriendly.value.startsWith("0Q"))
    assertTrue(mainnetFriendly.value.startsWith("UQ"))

    val parsedMain = TonAddress.parse(mainnetFriendly.value, Network.MAINNET).getOrThrow()
    assertEquals(mainnetFriendly.value, parsedMain.toFriendly(bounceable = false))
    assertEquals(rawHex, parsedMain.raw)

    assertTrue(TonAddress.parse(testnetFriendly.value, Network.MAINNET).isFailure)
    assertTrue(TonAddress.parse(mainnetFriendly.value, Network.TESTNET).isFailure)
    assertTrue(TonAddress.parse("${testnetFriendly.value.dropLast(1)}A", Network.TESTNET).isFailure)
  }

  @Test
  fun bip39_wordlist_and_mnemonic_generation_valid() {
    assertEquals(2048, Bip39Wordlist.WORDS.size)
    assertEquals("abandon", Bip39Wordlist.WORDS.first())
    assertEquals("zoo", Bip39Wordlist.WORDS.last())

    val generated = MnemonicService.generate12WordMnemonic()
    try {
      assertEquals(12, generated.wordCount)
      assertTrue(MnemonicService.validateBip39Mnemonic(generated))
    } finally {
      generated.wipe()
    }

    val invalidChecksum =
      MnemonicPhrase(List(12) { "abandon".toCharArray() })
    assertFalse(MnemonicService.validateBip39Mnemonic(invalidChecksum))
    invalidChecksum.wipe()
  }

  @Test
  fun slip0010_known_vector_derives_distinct_mainnet_and_testnet_v5r1_addresses() {
    val words =
      listOf(
        "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon", "abandon", "about",
      ).map { it.toCharArray() }
    val phrase = MnemonicPhrase(words)
    val derived1 = MnemonicService.deriveMultichainKey(phrase)
    val derived2 = MnemonicService.deriveMultichainKey(phrase)
    try {
      assertArrayEquals(derived1.seed32, derived2.seed32)
      assertArrayEquals(derived1.publicKey32, derived2.publicKey32)

      val mainnetAddr = AddressService.deriveV5R1Address(derived1.publicKey32, Network.MAINNET)
      val testnetAddr = AddressService.deriveV5R1Address(derived1.publicKey32, Network.TESTNET)

      // Both friendly and raw V5R1 addresses must differ for Mainnet (0x7FFFFF11) vs Testnet (0x7FFFFFFD)
      assertNotEquals(mainnetAddr.raw, testnetAddr.raw)
      val mainnetFriendly = AddressService.formatFriendly(mainnetAddr)
      val testnetFriendly = AddressService.formatFriendly(testnetAddr)
      assertTrue(mainnetFriendly.startsWith("UQ"))
      assertTrue(testnetFriendly.startsWith("0Q"))
      assertNotEquals(mainnetFriendly, testnetFriendly)

      // Round-trip validation
      assertEquals(
        mainnetFriendly,
        AddressService.parseAddress(mainnetFriendly, Network.MAINNET).getOrThrow().toFriendly(),
      )
      assertEquals(
        testnetFriendly,
        AddressService.parseAddress(testnetFriendly, Network.TESTNET).getOrThrow().toFriendly(),
      )
    } finally {
      phrase.wipe()
      derived1.wipe()
      derived2.wipe()
    }
  }

  @Test
  fun backup_challenge_verification_handles_match_and_mismatch() {
    val phrase = MnemonicService.generate12WordMnemonic()
    try {
      val challenge = MnemonicService.createBackupChallenge(wordCount = 12, sampleCount = 3)
      assertEquals(3, challenge.indicesOneBased.distinct().size)
      assertTrue(challenge.indicesOneBased.all { it in 1..12 })

      val correctAnswers =
        challenge.indicesOneBased.associateWith { idx -> phrase.wordAt(idx - 1) }
      assertTrue(MnemonicService.verifyBackupChallenge(phrase, challenge, correctAnswers))
      correctAnswers.values.forEach { it.fill('\u0000') }

      val wrongAnswers =
        challenge.indicesOneBased.associateWith { "invalidword".toCharArray() }
      assertFalse(MnemonicService.verifyBackupChallenge(phrase, challenge, wrongAnswers))
      wrongAnswers.values.forEach { it.fill('\u0000') }
    } finally {
      phrase.wipe()
    }
  }

  @Test
  fun qr_encoding_produces_valid_matrix() {
    val matrix = encodeQrMatrix("0QCSES0TZYqcVkgoguhIb8iMEo4cvaEwmIrU5qbQgnN8fo2A")
    assertTrue(matrix.size >= 21)
    assertEquals(matrix.size * matrix.size, matrix.modules.size)
    assertTrue(matrix.modules.any { it })
  }
}
