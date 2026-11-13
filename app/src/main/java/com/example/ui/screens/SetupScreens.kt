package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.BackupChallenge
import com.example.ui.components.SecureScreenEffect
import com.example.ui.theme.VaultBlue
import com.example.ui.theme.VaultDarkCard
import com.example.ui.theme.VaultDarkCardElevated
import java.util.Arrays

import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun WelcomeCreateScreen(
  isDeviceSecure: Boolean,
  isBusy: Boolean,
  errorMessage: String?,
  onCreateWallet: () -> Unit,
) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(horizontal = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Box(
          modifier =
            Modifier.size(72.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(Color(0xFF0077B6), VaultBlue))),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = stringResource(R.string.app_name),
            tint = Color.White,
            modifier = Modifier.size(36.dp),
          )
        }

        Text(
          text = stringResource(R.string.welcome_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )

        Text(
          text = stringResource(R.string.welcome_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = VaultDarkCard),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            SecurityPointRow(stringResource(R.string.welcome_security_point_1))
            SecurityPointRow(stringResource(R.string.welcome_security_point_2))
            SecurityPointRow(stringResource(R.string.welcome_security_point_3))
          }
        }

        if (!isDeviceSecure) {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("pin_fallback_info_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VaultDarkCardElevated),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = VaultBlue,
              )
              Text(
                text = stringResource(R.string.welcome_pin_fallback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().testTag("welcome_error_text"),
          )
        }

        Button(
          onClick = onCreateWallet,
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
          modifier =
            Modifier.fillMaxWidth().height(54.dp).testTag("welcome_create_wallet_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF001E30),
            )
            Spacer(modifier = Modifier.width(10.dp))
          } else {
            Icon(imageVector = Icons.Filled.AddCircleOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text(
            text = stringResource(R.string.welcome_create_button),
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
fun PinPromptDialog(
  isSetupMode: Boolean,
  errorMessage: String?,
  onDismiss: () -> Unit,
  onSubmitPin: (CharArray) -> Unit,
) {
  var pin by remember { androidx.compose.runtime.mutableStateOf("") }
  var confirmPin by remember { androidx.compose.runtime.mutableStateOf("") }
  var localError by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
  val lengthError = stringResource(R.string.pin_length_error)
  val mismatchError = stringResource(R.string.pin_mismatch_error)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Text(
        text =
          if (isSetupMode) stringResource(R.string.pin_setup_title)
          else stringResource(R.string.pin_enter_title),
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text =
            if (isSetupMode) stringResource(R.string.pin_setup_subtitle)
            else stringResource(R.string.pin_enter_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = pin,
          onValueChange = { value ->
            if (value.length <= 8 && value.all { it.isDigit() }) {
              pin = value
              localError = null
            }
          },
          label = { Text(stringResource(R.string.pin_field_label)) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth().testTag("vault_pin_input"),
        )
        if (isSetupMode) {
          OutlinedTextField(
            value = confirmPin,
            onValueChange = { value ->
              if (value.length <= 8 && value.all { it.isDigit() }) {
                confirmPin = value
                localError = null
              }
            },
            label = { Text(stringResource(R.string.pin_confirm_field_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("vault_pin_confirm_input"),
          )
        }
        val activeError = localError ?: errorMessage
        if (!activeError.isNullOrBlank()) {
          Text(
            text = activeError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("vault_pin_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pin.length < 4 || pin.length > 8) {
            localError = lengthError
          } else if (isSetupMode && pin != confirmPin) {
            localError = mismatchError
          } else {
            val chars = pin.toCharArray()
            pin = ""
            confirmPin = ""
            onSubmitPin(chars)
          }
        },
        modifier = Modifier.testTag("vault_pin_submit_button"),
      ) {
        Text(stringResource(R.string.pin_confirm_button))
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          pin = ""
          confirmPin = ""
          onDismiss()
        },
        modifier = Modifier.testTag("vault_pin_cancel_button"),
      ) {
        Text(stringResource(R.string.pin_cancel_button))
      }
    },
  )
}

@Composable
private fun SecurityPointRow(text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.VerifiedUser,
      contentDescription = null,
      tint = VaultBlue,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun RecoveryPhraseScreen(
  words: List<String>,
  isAlreadyBackedUp: Boolean,
  onBack: () -> Unit,
  onProceedToVerify: () -> Unit,
  onSkipBackup: () -> Unit,
) {
  SecureScreenEffect()
  BackHandler(onBack = onBack)

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 540.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("phrase_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.phrase_done),
            )
          }
          Text(
            text = stringResource(R.string.phrase_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          text = stringResource(R.string.phrase_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val half = (words.size + 1) / 2
          for (row in 0 until half) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              WordCell(
                number = row + 1,
                word = words.getOrElse(row) { "" },
                modifier = Modifier.weight(1f),
              )
              val rightIndex = row + half
              if (rightIndex < words.size) {
                WordCell(
                  number = rightIndex + 1,
                  word = words[rightIndex],
                  modifier = Modifier.weight(1f),
                )
              } else {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = VaultDarkCardElevated),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = VaultBlue,
            )
            Text(
              text = stringResource(R.string.phrase_scam_warning),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Button(
          onClick = onProceedToVerify,
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
          modifier =
            Modifier.fillMaxWidth().height(54.dp).testTag("proceed_backup_verify_button"),
        ) {
          Text(
            text =
              if (isAlreadyBackedUp) stringResource(R.string.phrase_done)
              else stringResource(R.string.phrase_continue_verify),
            fontWeight = FontWeight.Bold,
          )
        }

        if (!isAlreadyBackedUp) {
          TextButton(
            onClick = onSkipBackup,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("skip_backup_button"),
          ) {
            Text(text = stringResource(R.string.phrase_back_up_later))
          }
        }
      }
    }
  }
}

@Composable
private fun WordCell(number: Int, word: String, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = VaultDarkCard,
    modifier = modifier.testTag("mnemonic_word_$number"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "$number.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = word,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun BackupVerifyScreen(
  challenge: BackupChallenge,
  isBusy: Boolean,
  errorMessage: String?,
  onBack: () -> Unit,
  onSubmit: (Map<Int, CharArray>) -> Unit,
) {
  SecureScreenEffect()
  BackHandler(onBack = onBack)

  val inputs = remember(challenge) { mutableStateMapOf<Int, String>() }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("verify_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.phrase_title),
            )
          }
          Text(
            text = stringResource(R.string.verify_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          text = stringResource(R.string.verify_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        challenge.indicesOneBased.forEachIndexed { idx, wordNumber ->
          val isLast = idx == challenge.indicesOneBased.lastIndex
          OutlinedTextField(
            value = inputs[wordNumber].orEmpty(),
            onValueChange = { inputs[wordNumber] = it },
            label = { Text(stringResource(R.string.verify_word_label, wordNumber)) },
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
              ),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  if (!isBusy) {
                    val map =
                      challenge.indicesOneBased.associateWith { num ->
                        inputs[num].orEmpty().toCharArray()
                      }
                    onSubmit(map)
                  }
                }
              ),
            modifier = Modifier.fillMaxWidth().testTag("backup_input_$wordNumber"),
          )
        }

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("verify_error_text"),
          )
        }

        Button(
          onClick = {
            val map =
              challenge.indicesOneBased.associateWith { num -> inputs[num].orEmpty().toCharArray() }
            try {
              onSubmit(map)
            } finally {
              // Note: WalletCore zeroes char arrays after verification
            }
          },
          enabled = !isBusy && challenge.indicesOneBased.all { !inputs[it].isNullOrBlank() },
          colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("confirm_backup_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF001E30),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text(text = stringResource(R.string.verify_confirm_button), fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun LockedVaultScreen(
  isKeyInvalidated: Boolean,
  isBusy: Boolean,
  errorMessage: String?,
  onUnlock: () -> Unit,
  onResetVault: () -> Unit,
) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Box(
          modifier =
            Modifier.size(72.dp)
              .clip(CircleShape)
              .background(if (isKeyInvalidated) MaterialTheme.colorScheme.errorContainer else VaultDarkCard),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (isKeyInvalidated) Icons.Outlined.WarningAmber else Icons.Filled.Lock,
            contentDescription = stringResource(R.string.locked_title),
            tint = if (isKeyInvalidated) MaterialTheme.colorScheme.onErrorContainer else VaultBlue,
            modifier = Modifier.size(34.dp),
          )
        }

        Text(
          text =
            if (isKeyInvalidated) stringResource(R.string.key_invalidated_title)
            else stringResource(R.string.locked_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )

        Text(
          text =
            if (isKeyInvalidated) stringResource(R.string.key_invalidated_body)
            else stringResource(R.string.locked_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("locked_error_text"),
          )
        }

        if (isKeyInvalidated) {
          OutlinedButton(
            onClick = onResetVault,
            modifier =
              Modifier.fillMaxWidth().height(54.dp).testTag("reset_invalidated_vault_button"),
          ) {
            Text(text = stringResource(R.string.key_invalidated_reset_button))
          }
        } else {
          Button(
            onClick = onUnlock,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = VaultBlue, contentColor = Color(0xFF001E30)),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("unlock_wallet_button"),
          ) {
            Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.locked_unlock_button), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
