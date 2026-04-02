package com.myfitnessmeals.app.ui.barcode

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeCameraScannerDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onScannerError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanHandled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderState.value?.unbindAll()
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Scan barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Align the barcode inside the camera preview.",
                    style = MaterialTheme.typography.bodySmall,
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("meal_barcode_scanner_preview"),
                    factory = {
                        val previewView = PreviewView(it)
                        val providerFuture = ProcessCameraProvider.getInstance(it)
                        providerFuture.addListener(
                            {
                                try {
                                    val cameraProvider = providerFuture.get()
                                    cameraProviderState.value = cameraProvider

                                    val preview = androidx.camera.core.Preview.Builder()
                                        .build()
                                        .also { it.surfaceProvider = previewView.surfaceProvider }

                                    val analyzer = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImageProxy(
                                            imageProxy = imageProxy,
                                            scanner = scanner,
                                            scanHandled = scanHandled,
                                            onBarcodeScanned = onBarcodeScanned,
                                        )
                                    }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analyzer,
                                    )
                                } catch (_: Exception) {
                                    onScannerError("Unable to start camera scanner")
                                }
                            },
                            ContextCompat.getMainExecutor(it),
                        )
                        previewView
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("meal_barcode_scanner_close"),
            ) {
                Text("Close")
            }
        },
        modifier = Modifier.testTag("meal_barcode_scanner_dialog"),
    )
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanHandled: AtomicBoolean,
    onBarcodeScanned: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            if (scanHandled.get()) {
                return@addOnSuccessListener
            }

            val value = barcodes
                .firstOrNull { !it.rawValue.isNullOrBlank() }
                ?.rawValue
                ?.trim()
            if (!value.isNullOrBlank() && scanHandled.compareAndSet(false, true)) {
                onBarcodeScanned(value)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
