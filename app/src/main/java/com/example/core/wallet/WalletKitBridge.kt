package com.example.core.wallet

import android.content.Context
import com.example.core.keystore.SecureKeyStore
import com.example.core.model.Network
import com.example.core.model.TonAddress
import io.ton.walletkit.ITONWallet
import io.ton.walletkit.ITONWalletKit
import io.ton.walletkit.api.MAINNET
import io.ton.walletkit.api.TESTNET
import io.ton.walletkit.api.generated.TONNetwork
import io.ton.walletkit.config.TONWalletKitConfiguration
import io.ton.walletkit.model.TONHex
import io.ton.walletkit.model.WalletSigner
import io.ton.walletkit.storage.TONWalletKitStorageType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ton.kotlin.crypto.PrivateKeyEd25519

class WalletKitBridge(
  private val applicationContext: Context,
  private val secureKeyStore: SecureKeyStore,
) {
  suspend fun attachV5R1Wallet(
    publicKeyHex: String,
    network: Network,
    useAppPinMode: Boolean = false,
  ): TonAddress =
    kitMutex.withLock {
      val kit = ensureInitialized(applicationContext)
      val signer =
        object : WalletSigner {
          override fun publicKey(): TONHex = TONHex("0x$publicKeyHex")

          override suspend fun sign(data: ByteArray): TONHex =
            secureKeyStore.withDecryptedSeed(useAppPinMode = useAppPinMode) { seed32 ->
              val sig = PrivateKeyEd25519(seed32).signToByteArray(data, 0, data.size)
              TONHex("0x" + AddressService.bytesToHex(sig))
            }
        }
      val signerInfo = kit.createSignerFromCustom(signer)
      val adapter =
        kit.createV5R1Adapter(
          signer = signerInfo,
          network = network.toTonNetwork(),
          workchain = 0,
          walletId = network.v5r1WalletId,
        )
      val wallet: ITONWallet = kit.addWallet(adapter)
      TonAddress(raw = wallet.address.toRawString(), network = network)
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
