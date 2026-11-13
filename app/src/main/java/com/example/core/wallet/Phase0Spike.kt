package com.example.core.wallet

import android.content.Context
import com.example.core.model.Network
import io.ton.walletkit.ITONWalletKit
import io.ton.walletkit.api.MAINNET
import io.ton.walletkit.api.TESTNET
import io.ton.walletkit.api.generated.TONNetwork
import io.ton.walletkit.config.TONWalletKitConfiguration
import io.ton.walletkit.model.TONUserFriendlyAddress
import io.ton.walletkit.storage.TONWalletKitStorageType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Phase0WalletResult(
  val address: String,
  val rawAddress: String,
  val wordCount: Int,
  val network: Network,
)

class Phase0Spike(private val applicationContext: Context) {
  suspend fun createV5R1Wallet(network: Network): Phase0WalletResult =
    kitMutex.withLock {
      val kit = ensureInitialized(applicationContext)
      val tonNetwork = network.toTonNetwork()
      val mnemonic = kit.createTonMnemonic()
      val signer = kit.createSignerFromMnemonic(mnemonic = mnemonic)
      val adapter = kit.createV5R1Adapter(signer = signer, network = tonNetwork)
      val wallet = kit.addWallet(adapter)
      val nonBounceableAddress =
        TONUserFriendlyAddress(
            rawAddress = wallet.address.raw,
            isBounceable = false,
            isTestnetOnly = (network == Network.TESTNET),
          )
          .value
      Phase0WalletResult(
        address = nonBounceableAddress,
        rawAddress = wallet.address.toRawString(),
        wordCount = mnemonic.size,
        network = network,
      )
    }

  private fun Network.toTonNetwork(): TONNetwork =
    when (this) {
      Network.MAINNET -> TONNetwork.MAINNET
      Network.TESTNET -> TONNetwork.TESTNET
    }

  private companion object {
    val kitMutex = Mutex()
    @Volatile var sharedKit: ITONWalletKit? = null

    suspend fun ensureInitialized(appContext: Context): ITONWalletKit {
      sharedKit?.let { return it }
      val config =
        TONWalletKitConfiguration(
          networkConfigurations =
            setOf(
              TONWalletKitConfiguration.NetworkConfiguration(
                network = TONNetwork.MAINNET,
                apiClientConfiguration =
                  TONWalletKitConfiguration.APIClientConfiguration(
                    url = "https://toncenter.com",
                    key = "",
                  ),
                apiClientType = TONWalletKitConfiguration.APIClientType.TONCENTER,
              ),
              TONWalletKitConfiguration.NetworkConfiguration(
                network = TONNetwork.TESTNET,
                apiClientConfiguration =
                  TONWalletKitConfiguration.APIClientConfiguration(
                    url = "https://testnet.toncenter.com",
                    key = "",
                  ),
                apiClientType = TONWalletKitConfiguration.APIClientType.TONCENTER,
              ),
            ),
          walletManifest =
            TONWalletKitConfiguration.Manifest(
              name = "GramVault",
              appName = "gramvault",
              imageUrl = "https://gramvault.example/icon.png",
              aboutUrl = "https://gramvault.example",
              universalLink = "https://gramvault.example/ul",
              bridgeUrl = "https://bridge.tonapi.io/bridge",
            ),
          bridge = TONWalletKitConfiguration.Bridge(bridgeUrl = "https://bridge.tonapi.io/bridge"),
          features = listOf(TONWalletKitConfiguration.SendTransactionFeature()),
          storageType = TONWalletKitStorageType.Memory,
        )
      return ITONWalletKit.initialize(context = appContext, config = config).also {
        sharedKit = it
      }
    }
  }
}
