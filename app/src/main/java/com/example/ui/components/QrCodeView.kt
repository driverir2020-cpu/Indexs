package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class QrMatrix(val size: Int, val modules: BooleanArray) {
  fun isDark(x: Int, y: Int): Boolean = modules[y * size + x]
}

fun encodeQrMatrix(content: String): QrMatrix {
  val hints =
    mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.MARGIN to 1,
    )
  val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
  val width = bitMatrix.width
  val height = bitMatrix.height
  val side = maxOf(width, height)
  val modules = BooleanArray(side * side)
  for (y in 0 until height) {
    for (x in 0 until width) {
      modules[y * side + x] = bitMatrix.get(x, y)
    }
  }
  return QrMatrix(size = side, modules = modules)
}

@Composable
fun QrCodeCard(
  content: String,
  contentDescriptionText: String,
  modifier: Modifier = Modifier,
  qrSize: Dp = 200.dp,
) {
  val matrix = remember(content) { encodeQrMatrix(content) }
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(20.dp))
        .background(Color.White)
        .padding(14.dp)
        .semantics { contentDescription = contentDescriptionText },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(qrSize)) {
      val cellSize = size.width / matrix.size.toFloat()
      for (y in 0 until matrix.size) {
        for (x in 0 until matrix.size) {
          if (matrix.isDark(x, y)) {
            drawRect(
              color = Color(0xFF10161F),
              topLeft = Offset(x * cellSize, y * cellSize),
              size = Size(cellSize + 0.5f, cellSize + 0.5f),
            )
          }
        }
      }
    }
  }
}
