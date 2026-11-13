package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.Network
import com.example.core.model.VaultMetadata
import com.example.ui.components.QrCodeCard
import com.example.ui.theme.TestnetBannerOnRed
import com.example.ui.theme.TestnetBannerRed
import com.example.ui.theme.UsdtGreen
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletHomeScreen(
  metadata: VaultMetadata,
  activeNetwork: Network,
  bannerMessage: String?,
  onClearBanner: () -> Unit,
  onSelectNetwork: (Network) -> Unit,
  onOpenBackup: () -> Unit,
  onLockWallet: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val copiedText = stringResource(R.string.address_copied)
  var showReceiveSheet by remember { mutableStateOf(false) }

  val tonAddress = metadata.addressFor(activeNetwork)
  val friendlyAddress = tonAddress.toFriendly(bounceable = false)
  val rawAddress = tonAddress.raw

  LaunchedEffect(bannerMessage) {
    if (!bannerMessage.isNullOrBlank()) {
      snackbarHostState.showSnackbar(bannerMessage)
      onClearBanner()
    }
  }

  val copyAddress: (String) -> Unit = { address ->
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("TON Address", address))
    scope.launch { snackbarHostState.showSnackbar(copiedText) }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      WalletTopBar(
        activeNetwork = activeNetwork,
        onSelectNetwork = onSelectNetwork,
        onLockWallet = onLockWallet,
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (!metadata.isBackedUp) {
          Card(
            modifier =
              Modifier.fillMaxWidth()
                .clickable(onClick = onOpenBackup)
                .testTag("backup_warning_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF332612)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFCA28),
              )
              Text(
                text = stringResource(R.string.backup_needed_banner),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFE082),
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }

        HeroBalanceCard(
          activeNetwork = activeNetwork,
          friendlyAddress = friendlyAddress,
          isBackedUp = metadata.isBackedUp,
          onCopy = { copyAddress(friendlyAddress) },
        )

        ActionRow(
          onReceive = { showReceiveSheet = true },
          onCopy = { copyAddress(friendlyAddress) },
          onBackup = onOpenBackup,
        )

        AssetsCard()
        CoreStatusCard()

        if (showReceiveSheet) {
          ModalBottomSheet(
            onDismissRequest = { showReceiveSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            ReceiveSheetContent(
              network = activeNetwork,
              friendlyAddress = friendlyAddress,
              rawAddress = rawAddress,
              onCopyBase64 = {
                copyAddress(friendlyAddress)
                showReceiveSheet = false
              },
              onCopyRaw = {
                copyAddress(rawAddress)
                showReceiveSheet = false
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WalletTopBar(
  activeNetwork: Network,
  onSelectNetwork: (Network) -> Unit,
  onLockWallet: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
    if (activeNetwork == Network.TESTNET) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(TestnetBannerRed)
            .padding(vertical = 6.dp)
            .testTag("testnet_banner"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.testnet_active_warning),
          color = TestnetBannerOnRed,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(Color(0xFF0077B6), VaultBlue))),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = stringResource(R.string.app_name),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.wallet_badge_v5r1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = onLockWallet,
          modifier = Modifier.testTag("lock_wallet_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.action_lock),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        NetworkTogglePill(activeNetwork = activeNetwork, onSelectNetwork = onSelectNetwork)
      }
    }
  }
}

@Composable
private fun NetworkTogglePill(activeNetwork: Network, onSelectNetwork: (Network) -> Unit) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = VaultDarkCard,
    modifier = Modifier.testTag("network_toggle_pill"),
  ) {
    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      NetworkChip(
        label = stringResource(R.string.network_mainnet),
        selected = activeNetwork == Network.MAINNET,
        selectedColor = VaultBlue,
        tag = "network_mainnet_button",
        onClick = { onSelectNetwork(Network.MAINNET) },
      )
      NetworkChip(
        label = stringResource(R.string.network_testnet),
        selected = activeNetwork == Network.TESTNET,
        selectedColor = TestnetBannerRed,
        tag = "network_testnet_button",
        onClick = { onSelectNetwork(Network.TESTNET) },
      )
    }
  }
}

@Composable
private fun NetworkChip(
  label: String,
  selected: Boolean,
  selectedColor: Color,
  tag: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(999.dp))
        .background(if (selected) selectedColor else Color.Transparent)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HeroBalanceCard(
  activeNetwork: Network,
  friendlyAddress: String,
  isBackedUp: Boolean,
  onCopy: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("wallet_hero_card"),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = VaultDarkCard),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(Brush.verticalGradient(listOf(Color(0xFF1A2A3B), VaultDarkCard)))
          .padding(24.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text =
              if (activeNetwork == Network.MAINNET) stringResource(R.string.network_mainnet)
              else stringResource(R.string.network_testnet),
            style = MaterialTheme.typography.labelMedium,
            color = if (activeNetwork == Network.MAINNET) VaultBlue else Color(0xFFFF8A80),
            fontWeight = FontWeight.SemiBold,
          )
          if (isBackedUp) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.testTag("backed_up_badge"),
            ) {
              Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = UsdtGreen,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = stringResource(R.string.backup_verified_badge),
                style = MaterialTheme.typography.labelSmall,
                color = UsdtGreen,
              )
            }
          }
        }
        Text(
          text = stringResource(R.string.wallet_balance_pending),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = VaultDarkCardElevated,
          modifier = Modifier.clickable(onClick = onCopy).minimumInteractiveComponentSize(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = shortenAddress(friendlyAddress),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("wallet_address_text"),
            )
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = stringResource(R.string.action_copy),
              tint = VaultBlue,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActionRow(
  onReceive: () -> Unit,
  onCopy: () -> Unit,
  onBackup: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    WalletActionButton(
      icon = Icons.Filled.ArrowDownward,
      label = stringResource(R.string.action_receive),
      primary = true,
      tag = "receive_button",
      modifier = Modifier.weight(1f),
      onClick = onReceive,
    )
    WalletActionButton(
      icon = Icons.Filled.ContentCopy,
      label = stringResource(R.string.action_copy),
      primary = false,
      tag = "copy_address_button",
      modifier = Modifier.weight(1f),
      onClick = onCopy,
    )
    WalletActionButton(
      icon = Icons.Filled.Key,
      label = stringResource(R.string.action_backup),
      primary = false,
      tag = "backup_phrase_button",
      modifier = Modifier.weight(1f),
      onClick = onBackup,
    )
  }
}

@Composable
private fun WalletActionButton(
  icon: ImageVector,
  label: String,
  primary: Boolean,
  tag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (primary) VaultBlue else VaultDarkCard,
    modifier =
      modifier.height(74.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).testTag(tag),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (primary) Color(0xFF001E30) else VaultBlue,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (primary) Color(0xFF001E30) else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun AssetsCard() {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("assets_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = VaultDarkCard),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      Text(
        text = stringResource(R.string.assets_section_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
      )
      AssetRow(
        icon = Icons.Outlined.Diamond,
        iconBg = VaultBlue,
        name = stringResource(R.string.asset_gram_name),
        subtitle = stringResource(R.string.asset_gram_subtitle),
        balance = stringResource(R.string.asset_balance_pending),
      )
      HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 20.dp),
      )
      AssetRow(
        icon = Icons.Outlined.CurrencyExchange,
        iconBg = UsdtGreen,
        name = stringResource(R.string.asset_usdt_name),
        subtitle = stringResource(R.string.asset_usdt_subtitle),
        balance = stringResource(R.string.asset_balance_pending),
      )
    }
  }
}

@Composable
private fun AssetRow(
  icon: ImageVector,
  iconBg: Color,
  name: String,
  subtitle: String,
  balance: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(40.dp).clip(CircleShape).background(iconBg),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = name,
        tint = Color.White,
        modifier = Modifier.size(22.dp),
      )
    }
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      text = balance,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CoreStatusCard() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = VaultDarkCardElevated),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = stringResource(R.string.core_status_title),
        style = MaterialTheme.typography.labelLarge,
        color = VaultBlue,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(R.string.core_status_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReceiveSheetContent(
  network: Network,
  friendlyAddress: String,
  rawAddress: String,
  onCopyBase64: () -> Unit,
  onCopyRaw: () -> Unit,
) {
  var qrAsTransferLink by remember { mutableStateOf(false) }
  val qrPayload = if (qrAsTransferLink) "ton://transfer/$friendlyAddress" else friendlyAddress

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = stringResource(R.string.receive_sheet_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      text =
        if (network == Network.MAINNET) stringResource(R.string.receive_mainnet_note)
        else stringResource(R.string.receive_testnet_note),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (network == Network.MAINNET) MaterialTheme.colorScheme.onSurfaceVariant
        else Color(0xFFFF8A80),
      modifier = Modifier.fillMaxWidth(),
    )

    Surface(shape = RoundedCornerShape(999.dp), color = VaultDarkCard) {
      Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NetworkChip(
          label = stringResource(R.string.qr_mode_address),
          selected = !qrAsTransferLink,
          selectedColor = VaultBlue,
          tag = "qr_mode_address_button",
          onClick = { qrAsTransferLink = false },
        )
        NetworkChip(
          label = stringResource(R.string.qr_mode_link),
          selected = qrAsTransferLink,
          selectedColor = VaultBlue,
          tag = "qr_mode_link_button",
          onClick = { qrAsTransferLink = true },
        )
      }
    }

    QrCodeCard(
      content = qrPayload,
      contentDescriptionText = stringResource(R.string.qr_code_cd),
      modifier = Modifier.testTag("receive_qr_code"),
    )

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = VaultDarkCard,
      modifier = Modifier.fillMaxWidth(),
    ) {
      SelectionContainer {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.receive_base64_label),
            style = MaterialTheme.typography.labelMedium,
            color = VaultBlue,
          )
          Text(
            text = friendlyAddress,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.testTag("receive_full_address_text"),
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          Text(
            text = stringResource(R.string.receive_raw_label),
            style = MaterialTheme.typography.labelMedium,
            color = VaultBlue,
          )
          Text(
            text = rawAddress,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("receive_raw_address_text"),
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Button(
        onClick = onCopyBase64,
        colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
        modifier = Modifier.weight(1f).height(50.dp).testTag("sheet_copy_button"),
      ) {
        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.action_copy), fontWeight = FontWeight.SemiBold)
      }
      Button(
        onClick = onCopyRaw,
        colors = ButtonDefaults.buttonColors(containerColor = VaultDarkCardElevated, contentColor = Color.White),
        modifier = Modifier.weight(1f).height(50.dp).testTag("sheet_copy_raw_button"),
      ) {
        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.action_copy_raw), fontWeight = FontWeight.SemiBold)
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
  }
}

private fun shortenAddress(address: String): String {
  if (address.length <= 14) return address
  return "${address.take(6)}...${address.takeLast(6)}"
}
