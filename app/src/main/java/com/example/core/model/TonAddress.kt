package com.example.core.model

import org.ton.block.AddrStd

data class TonAddress(
  val raw: String,
  val network: Network,
) {
  fun toFriendly(bounceable: Boolean = false): String {
    val std = AddrStd(raw)
    return std.toString(
      userFriendly = true,
      urlSafe = true,
      testOnly = (network == Network.TESTNET),
      bounceable = bounceable,
    )
  }

  companion object {
    fun parse(input: String, expectedNetwork: Network): Result<TonAddress> = runCatching {
      val trimmed = input.trim()
      require(trimmed.isNotEmpty()) { "Address cannot be empty" }
      if (trimmed.contains(':')) {
        val std = AddrStd.parseRaw(trimmed)
        require(std.workchainId == 0 || std.workchainId == -1) {
          "Unsupported workchain: ${std.workchainId}"
        }
        TonAddress(raw = std.toString(userFriendly = false), network = expectedNetwork)
      } else {
        val parsed = io.ton.walletkit.model.TONUserFriendlyAddress(trimmed)
        val normalizedB64 = trimmed.replace('-', '+').replace('_', '/')
        val decoded = java.util.Base64.getDecoder().decode(normalizedB64)
        require(decoded.size == 36) { "Invalid friendly address length" }
        val addressIsTestnet = (decoded[0].toInt() and 0x80) != 0
        val expectTestnet = (expectedNetwork == Network.TESTNET)
        require(addressIsTestnet == expectTestnet) {
          if (expectTestnet) "Mainnet address is not allowed on Testnet"
          else "Testnet address is not allowed on Mainnet"
        }
        val std = AddrStd(parsed.toRawString())
        TonAddress(raw = std.toString(userFriendly = false), network = expectedNetwork)
      }
    }
  }
}
