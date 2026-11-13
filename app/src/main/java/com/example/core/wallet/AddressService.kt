package com.example.core.wallet

import com.example.core.model.Network
import com.example.core.model.TonAddress
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder

object AddressService {
  // Wallet V5R1 compiled code BOC verified from org.ton:walletkit:0.0.6
  private const val WALLET_V5R1_CODE_BOC_HEX =
    "b5ee9c7201021401000281000114ff00f4a413f4bcf2c80b01020120020302014804050102f20e02dcd020d749c120" +
      "915b8f6320d70b1f2082106578746ebd21821073696e74bdb0925f03e082106578746eba8eb48020d72101d074d721" +
      "fa4030fa44f828fa443058bd915be0ed44d0810141d721f4058307f40e6fa1319130e18040d721707fdb3ce03120d74" +
      "9810280b99130e070e2100f020120060702012008090019be5f0f6a2684080a0eb90fa02c02016e0a0b0201480c0d00" +
      "19adce76a2684020eb90eb85ffc00019af1df6a2684010eb90eb858fc00017b325fb51341c75c875c2c7e00011b262f" +
      "b513435c28020011e20d70b1f82107369676ebaf2e08a7f0f01e68ef0eda2edfb218308d722028308d723208020d721" +
      "d31fd31fd31fed44d0d200d31f20d31fd3ffd70a000af90140ccf9109a28945f0adb31e1f2c087df02b35007b0f2d08" +
      "45125baf2e0855036baf2e086f823bbf2d0882292f800de01a47fc8ca00cb1f01cf16c9ed542092f80fde70db3cd810" +
      "03f6eda2edfb02f404216e926c218e4c0221d73930709421c700b38e2d01d72820761e436c20d749c008f2e09320d74" +
      "ac002f2e09320d71d06c712c2005230b0f2d089d74cd7393001a4e86c128407bbf2e093d74ac000f2e093ed55e2d200" +
      "01c000915be0ebd72c08142091709601d72c081c12e25210b1e30f20d74a111213009601fa4001fa44f828fa443058ba" +
      "f2e091ed44d0810141d718f405049d7fc8ca0040048307f453f2e08b8e14038307f45bf2e08c22d70a00216e01b3b0f2" +
      "d090e2c85003cf1612f400c9ed54007230d72c08248e2d21f2e092d200ed44d0d2005113baf2d08f54503091319c0181" +
      "0140d721d70a00f2e08ee2c8ca0058cf16c9ed5493f2c08de20010935bdb31e1d74cd0"

  private val v5r1CodeCell: Cell by lazy {
    BagOfCells(hexToBytes(WALLET_V5R1_CODE_BOC_HEX)).roots.first()
  }

  fun deriveV5R1Address(
    publicKey32: ByteArray,
    network: Network,
    workchain: Int = 0,
  ): TonAddress {
    require(publicKey32.size == 32) { "Ed25519 public key must be 32 bytes" }
    val dataCell =
      CellBuilder.createCell {
        storeBit(true) // signatureAllowed
        storeUInt(0, 32) // initial seqno
        storeUInt(network.v5r1WalletId, 32) // TEP-3 network-specific wallet_id
        storeBytes(publicKey32, 32) // 256-bit Ed25519 public key
        storeBit(false) // empty extensions dictionary
      }
    val stateInit = StateInit(code = v5r1CodeCell, data = dataCell)
    val addrStd = stateInit.address(workchain)
    return TonAddress(raw = addrStd.toString(userFriendly = false), network = network)
  }

  fun formatFriendly(address: TonAddress, bounceable: Boolean = false): String =
    address.toFriendly(bounceable = bounceable)

  fun parseAddress(input: String, expectedNetwork: Network): Result<TonAddress> =
    TonAddress.parse(input, expectedNetwork)

  fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
      hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }

  fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString(separator = "") { "%02x".format(it) }
}
