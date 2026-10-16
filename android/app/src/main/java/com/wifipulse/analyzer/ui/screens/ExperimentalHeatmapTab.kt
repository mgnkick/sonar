package com.wifipulse.analyzer.ui.screens

import android.content.Context
import android.graphics.Paint as AndroidPaint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.ImageDecoder
import android.os.Build
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

private data class CoverageSample(val x: Float, val y: Float, val rssi: Int, val bssid: String, val time: Long)
private val HeatCyan = Color(0xFF55F4FF)
private const val HEAT_PREFS = "wifi_pulse_experimental_coverage"
private const val HEAT_KEY = "samples"
private const val PLAN_KEY = "plan_uri"
private const val SHAPES_KEY = "sketch_shapes"

private enum class MapTool { MEASURE, SELECT_OBJECT, ERASER, LINE, RECTANGLE, CIRCLE }
private enum class SketchKind { LINE, RECTANGLE, CIRCLE }
private data class SketchShape(val kind: SketchKind, val x1: Float, val y1: Float, val x2: Float, val y2: Float)

@Composable
fun ExperimentalHeatmapTab(connectionDetails: WifiConnectionDetails) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(HEAT_PREFS, Context.MODE_PRIVATE) }
    var samples by remember { mutableStateOf(readSamples(preferences.getString(HEAT_KEY, null))) }
    var sketchShapes by remember { mutableStateOf(readShapes(preferences.getString(SHAPES_KEY, null))) }
    var selectedPointTime by remember { mutableStateOf<Long?>(null) }
    var selectedShapeIndex by remember { mutableIntStateOf(-1) }
    var currentTool by remember { mutableStateOf(MapTool.MEASURE) }
    var drawStart by remember { mutableStateOf<Offset?>(null) }
    var drawPreviewEnd by remember { mutableStateOf<Offset?>(null) }
    var showFullscreen by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
        val ratio = newZoom / zoom
        zoom = newZoom
        pan = pan * ratio + panChange
    }
    var planUriString by remember { mutableStateOf(preferences.getString(PLAN_KEY, null)) }
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var planLoadFailed by remember(planUriString) { mutableStateOf(false) }
    val planBitmap by produceState<Bitmap?>(null, planUriString) {
        val uri = planUriString
        if (uri == null) {
            value = null
        } else {
            val result = withContext(Dispatchers.IO) { runCatching { decodePlanBitmap(context, android.net.Uri.parse(uri)) } }
            value = result.getOrNull()
            planLoadFailed = result.isFailure || result.getOrNull() == null
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            planUriString = uri.toString()
            preferences.edit().putString(PLAN_KEY, planUriString).apply()
            samples = emptyList()
            sketchShapes = emptyList()
            selectedPointTime = null
            selectedShapeIndex = -1
            preferences.edit().remove(HEAT_KEY).apply()
            preferences.edit().remove(SHAPES_KEY).apply()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            planUriString = uri.toString()
            preferences.edit().putString(PLAN_KEY, planUriString).apply()
            samples = emptyList()
            sketchShapes = emptyList()
            selectedPointTime = null
            selectedShapeIndex = -1
            preferences.edit().remove(HEAT_KEY).apply()
            preferences.edit().remove(SHAPES_KEY).apply()
        }
    }
    val currentRssi = connectionDetails.rssi.takeIf { connectionDetails.isConnected && it in -100..0 }
    val currentBssid = connectionDetails.bssid.takeIf { it.isNotBlank() && it != "BSSID недоступен" } ?: "BSSID недоступен"

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Image, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Загрузить план")
            }
            OutlinedButton(onClick = {
                val dir = File(context.filesDir, "experimental").apply { mkdirs() }
                val photo = File(dir, "coverage-plan-${System.currentTimeMillis()}.jpg")
                val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
                pendingCameraUri = outputUri
                camera.launch(outputUri)
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Сделать фото")
            }
        }
        Text("Поддерживаются JPG/JPEG, PNG, BMP, GIF и WebP (у анимации берётся первый кадр). HEIC/HEIF — Android 9+, AVIF — Android 12+ при поддержке устройством. PDF и SVG не поддерживаются.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (planLoadFailed) {
            Text("Не удалось открыть изображение. Выберите JPG или PNG и попробуйте ещё раз.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (planUriString != null) {
            TextButton(onClick = {
                planUriString?.let { uri -> runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                planUriString = null
                preferences.edit().remove(PLAN_KEY).apply()
                samples = emptyList()
                sketchShapes = emptyList()
                selectedPointTime = null
                selectedShapeIndex = -1
                preferences.edit().remove(HEAT_KEY).apply()
                preferences.edit().remove(SHAPES_KEY).apply()
            }) { Text("Убрать изображение и начать заново") }
        }
        Text("ИНСТРУМЕНТЫ ПЛАНА", color = HeatCyan, style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = currentTool == MapTool.MEASURE, onClick = { currentTool = MapTool.MEASURE; drawStart = null; drawPreviewEnd = null }, label = { Text("Замер") }) }
            item { FilterChip(selected = currentTool == MapTool.LINE, onClick = { currentTool = MapTool.LINE; drawStart = null; drawPreviewEnd = null }, label = { Text("Линия") }) }
            item { FilterChip(selected = currentTool == MapTool.RECTANGLE, onClick = { currentTool = MapTool.RECTANGLE; drawStart = null; drawPreviewEnd = null }, label = { Text("Прямоугольник") }) }
            item { FilterChip(selected = currentTool == MapTool.CIRCLE, onClick = { currentTool = MapTool.CIRCLE; drawStart = null; drawPreviewEnd = null }, label = { Text("Круг") }) }
            item { FilterChip(selected = currentTool == MapTool.SELECT_OBJECT, onClick = { currentTool = MapTool.SELECT_OBJECT; drawStart = null; drawPreviewEnd = null }, label = { Text("Выбрать объект") }) }
            item { FilterChip(selected = currentTool == MapTool.ERASER, onClick = { currentTool = MapTool.ERASER; drawStart = null; drawPreviewEnd = null }, label = { Text("Ластик") }) }
        }
        Text(
            when (currentTool) {
                MapTool.MEASURE -> "Коснитесь плана, чтобы поставить замер; двумя пальцами можно менять масштаб и перемещать план."
                MapTool.SELECT_OBJECT -> "Коснитесь метки или контура, чтобы выбрать объект; удалите его отдельной кнопкой."
                MapTool.ERASER -> "Коснитесь метки или контура, чтобы сразу стереть ближайший объект."
                MapTool.LINE -> "Проведите пальцем от начала линии до её конца."
                MapTool.RECTANGLE -> "Проведите пальцем от одного угла прямоугольника к противоположному."
                MapTool.CIRCLE -> "Проведите пальцем от одного края круга к противоположному."
            },
            style = MaterialTheme.typography.labelSmall,
            color = HeatCyan
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, enabled = zoom > 1f || pan != Offset.Zero) { Text("Сбросить масштаб") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showFullscreen = true }) { Icon(Icons.Default.Fullscreen, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("На весь экран · альбомно") }
        }
        Surface(color = Color(0xFF11162A), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, HeatCyan.copy(alpha = 0.55f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ЭКСПЕРИМЕНТАЛЬНОЕ · КАРТА ПОКРЫТИЯ", color = HeatCyan, style = MaterialTheme.typography.labelLarge, letterSpacing = 0.8.sp)
                Text("Переместитесь в нужную зону и коснитесь её на схеме. В точку запишутся текущие RSSI и BSSID.", style = MaterialTheme.typography.bodyMedium)
                Text("Сейчас: " + (currentRssi?.let { it.toString() + " дБм" } ?: "нет достоверного RSSI") + " · " + currentBssid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val onMapTap: (Offset) -> Unit = { point ->
            when (currentTool) {
                MapTool.MEASURE -> if (currentRssi != null) {
                    val timestamp = maxOf(System.currentTimeMillis(), (samples.lastOrNull()?.time ?: 0L) + 1L)
                    samples = (samples + CoverageSample(point.x, point.y, currentRssi, currentBssid, timestamp)).takeLast(200)
                    selectedPointTime = timestamp
                    selectedShapeIndex = -1
                    preferences.edit().putString(HEAT_KEY, writeSamples(samples)).apply()
                }
                MapTool.SELECT_OBJECT, MapTool.ERASER -> {
                    val sampleHit = samples.map { sample ->
                        val dx = sample.x - point.x
                        val dy = sample.y - point.y
                        sample to (dx * dx + dy * dy)
                    }.filter { it.second < 0.0064f / (zoom * zoom) }
                        .minByOrNull { it.second }
                    val shapeHit = sketchShapes.mapIndexed { index, shape ->
                        index to distanceToShape(point, shape)
                    }.filter { it.second < 0.0036f / (zoom * zoom) }
                        .minByOrNull { it.second }
                    val hitSampleFirst = sampleHit != null && (shapeHit == null || sampleHit.second <= shapeHit.second)
                    if (currentTool == MapTool.SELECT_OBJECT) {
                        selectedPointTime = if (hitSampleFirst) sampleHit?.first?.time else null
                        selectedShapeIndex = if (!hitSampleFirst) shapeHit?.first ?: -1 else -1
                    } else if (hitSampleFirst) {
                        val erasedTime = sampleHit?.first?.time
                        samples = samples.filterNot { it.time == erasedTime }
                        selectedPointTime = null
                        selectedShapeIndex = -1
                        preferences.edit().putString(HEAT_KEY, writeSamples(samples)).apply()
                    } else if (shapeHit != null) {
                        sketchShapes = sketchShapes.filterIndexed { index, _ -> index != shapeHit.first }
                        selectedPointTime = null
                        selectedShapeIndex = -1
                        preferences.edit().putString(SHAPES_KEY, writeShapes(sketchShapes)).apply()
                    }
                }
                else -> Unit
            }
        }
        val onShapeDrag: (Offset, Offset) -> Unit = { start, end ->
            val kind = when (currentTool) {
                MapTool.LINE -> SketchKind.LINE
                MapTool.RECTANGLE -> SketchKind.RECTANGLE
                else -> SketchKind.CIRCLE
            }
            sketchShapes = sketchShapes + SketchShape(kind, start.x, start.y, end.x, end.y)
            selectedPointTime = null
            selectedShapeIndex = sketchShapes.lastIndex
            preferences.edit().putString(SHAPES_KEY, writeShapes(sketchShapes)).apply()
        }
        val onShapeDragPreview: (Offset?, Offset?) -> Unit = { start, end ->
            drawStart = start
            drawPreviewEnd = end
        }
        val clearShapePreview = {
            drawStart = null
            drawPreviewEnd = null
        }
        val deleteSelectedObject: () -> Unit = {
            if (selectedPointTime != null) {
                samples = samples.filterNot { it.time == selectedPointTime }
                selectedPointTime = null
                preferences.edit().putString(HEAT_KEY, writeSamples(samples)).apply()
            } else if (selectedShapeIndex in sketchShapes.indices) {
                sketchShapes = sketchShapes.filterIndexed { index, _ -> index != selectedShapeIndex }
                selectedShapeIndex = -1
                preferences.edit().putString(SHAPES_KEY, writeShapes(sketchShapes)).apply()
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF090D19)), border = androidx.compose.foundation.BorderStroke(1.dp, HeatCyan.copy(alpha = 0.65f))) {
            CoverageMapCanvas(
                modifier = Modifier.fillMaxWidth().height(340.dp),
                samples = samples,
                sketchShapes = sketchShapes,
                planBitmap = planBitmap,
                tool = currentTool,
                drawStart = drawStart,
                drawPreviewEnd = drawPreviewEnd,
                selectedPointTime = selectedPointTime,
                selectedShapeIndex = selectedShapeIndex,
                zoom = zoom,
                pan = pan,
                transformState = transformState,
                onMapTap = onMapTap,
                onShapeDrag = onShapeDrag,
                onShapeDragPreview = onShapeDragPreview,
                clearShapePreview = clearShapePreview
            )
        }
        if (showFullscreen) {
            val activity = context.findActivity()
            val previousOrientation = remember(activity) { activity?.requestedOrientation }
            DisposableEffect(activity) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                onDispose { if (previousOrientation != null) activity?.requestedOrientation = previousOrientation }
            }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showFullscreen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF070B13)) {
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("КАРТА ПОКРЫТИЯ · ТСАП", color = HeatCyan, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Сброс масштаба") }
                            IconButton(onClick = { showFullscreen = false }) { Icon(Icons.Default.FullscreenExit, contentDescription = "Закрыть полноэкранный режим", tint = HeatCyan) }
                        }
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { FilterChip(selected = currentTool == MapTool.MEASURE, onClick = { currentTool = MapTool.MEASURE; drawStart = null; drawPreviewEnd = null }, label = { Text("Замер") }) }
                            item { FilterChip(selected = currentTool == MapTool.LINE, onClick = { currentTool = MapTool.LINE; drawStart = null; drawPreviewEnd = null }, label = { Text("Линия") }) }
                            item { FilterChip(selected = currentTool == MapTool.RECTANGLE, onClick = { currentTool = MapTool.RECTANGLE; drawStart = null; drawPreviewEnd = null }, label = { Text("Прямоугольник") }) }
                            item { FilterChip(selected = currentTool == MapTool.CIRCLE, onClick = { currentTool = MapTool.CIRCLE; drawStart = null; drawPreviewEnd = null }, label = { Text("Круг") }) }
                            item { FilterChip(selected = currentTool == MapTool.SELECT_OBJECT, onClick = { currentTool = MapTool.SELECT_OBJECT; drawStart = null; drawPreviewEnd = null }, label = { Text("Выбрать объект") }) }
                            item { FilterChip(selected = currentTool == MapTool.ERASER, onClick = { currentTool = MapTool.ERASER; drawStart = null; drawPreviewEnd = null }, label = { Text("Ластик") }) }
                        }
                        Text(
                            when (currentTool) {
                                MapTool.MEASURE -> "Коснитесь плана, чтобы поставить точку замера."
                                MapTool.SELECT_OBJECT -> "Коснитесь точки или фигуры, чтобы выделить её."
                                MapTool.ERASER -> "Коснитесь объекта, чтобы сразу стереть его."
                                MapTool.LINE -> "Проведите пальцем от начала линии до конца."
                                MapTool.RECTANGLE -> "Перетащите палец от одного угла к противоположному."
                                MapTool.CIRCLE -> "Перетащите палец от одного края круга к противоположному."
                            },
                            color = HeatCyan,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (selectedPointTime != null || selectedShapeIndex in sketchShapes.indices) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Объект выбран", color = Color(0xFFFFC857), modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = deleteSelectedObject) { Text("Удалить выбранное") }
                            }
                        }
                        CoverageMapCanvas(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            samples = samples,
                            sketchShapes = sketchShapes,
                            planBitmap = planBitmap,
                            tool = currentTool,
                            drawStart = drawStart,
                            drawPreviewEnd = drawPreviewEnd,
                            selectedPointTime = selectedPointTime,
                            selectedShapeIndex = selectedShapeIndex,
                            zoom = zoom,
                            pan = pan,
                            transformState = transformState,
                            onMapTap = onMapTap,
                            onShapeDrag = onShapeDrag,
                            onShapeDragPreview = onShapeDragPreview,
                            clearShapePreview = clearShapePreview
                        )
                    }
                }
            }
        }
        if (selectedPointTime != null || selectedShapeIndex >= 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val selected = samples.firstOrNull { it.time == selectedPointTime }
                val selectedShape = sketchShapes.getOrNull(selectedShapeIndex)
                Text(
                    selected?.let { "Выбрана точка замера · ${it.rssi} дБм · ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it.time))}" }
                        ?: selectedShape?.let { "Выбрана фигура: ${when (it.kind) { SketchKind.LINE -> "линия"; SketchKind.RECTANGLE -> "прямоугольник"; SketchKind.CIRCLE -> "круг" }}" }
                        ?: "Объект не выбран",
                    modifier = Modifier.weight(1f), color = HeatCyan, style = MaterialTheme.typography.labelMedium
                )
                OutlinedButton(onClick = deleteSelectedObject) { Text("Удалить выбранное") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeatLegend(Color(0xFF55F4FF), "Сильный ≥ −50")
            HeatLegend(Color(0xFFFFC857), "Средний −65")
            HeatLegend(Color(0xFFFF5364), "Слабый ≤ −85")
        }
        Text(if (planBitmap == null) "ПУСТАЯ СХЕМА · рисуйте помещение инструментами выше. Точки хранятся локально; цвет между ними — интерполяция измеренного RSSI, не прогноз покрытия." else "ПЛАН/ФОТО · точки привязаны к изображению и хранятся локально. Интерполяция показывает RSSI между реальными замерами.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Измерений: " + samples.size, style = MaterialTheme.typography.titleSmall, color = HeatCyan)
            TextButton(onClick = {
                val removed = samples.lastOrNull()
                samples = samples.dropLast(1)
                if (removed?.time == selectedPointTime) selectedPointTime = null
                if (samples.isEmpty()) preferences.edit().remove(HEAT_KEY).apply()
                else preferences.edit().putString(HEAT_KEY, writeSamples(samples)).apply()
            }, enabled = samples.isNotEmpty()) {
                Icon(Icons.Default.Undo, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Удалить последнюю точку")
            }
        }
        if (sketchShapes.isNotEmpty()) {
            TextButton(onClick = {
                val removedIndex = sketchShapes.lastIndex
                sketchShapes = sketchShapes.dropLast(1)
                if (selectedShapeIndex == removedIndex) selectedShapeIndex = -1
                preferences.edit().putString(SHAPES_KEY, writeShapes(sketchShapes)).apply()
            }) { Text("Отменить последнюю фигуру") }
        }
        Text(
            if (currentRssi == null) "Подключите Wi-Fi, чтобы записывать реальные точки уровня сигнала."
            else "Переместитесь в нужную зону и коснитесь её на плане для записи измерения.",
            color = if (currentRssi == null) MaterialTheme.colorScheme.error else HeatCyan,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CoverageMapCanvas(
    modifier: Modifier,
    samples: List<CoverageSample>,
    sketchShapes: List<SketchShape>,
    planBitmap: Bitmap?,
    tool: MapTool,
    drawStart: Offset?,
    drawPreviewEnd: Offset?,
    selectedPointTime: Long?,
    selectedShapeIndex: Int,
    zoom: Float,
    pan: Offset,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    onMapTap: (Offset) -> Unit,
    onShapeDrag: (Offset, Offset) -> Unit,
    onShapeDragPreview: (Offset?, Offset?) -> Unit,
    clearShapePreview: () -> Unit
) {
    val interpolationSamples = remember(samples) { samples.takeLast(60) }
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .transformable(transformState, enabled = tool == MapTool.MEASURE || tool == MapTool.SELECT_OBJECT || tool == MapTool.ERASER)
            .pointerInput(tool, samples, sketchShapes, zoom, pan, planBitmap) {
                fun mapPosition(position: Offset): Offset? {
                    val viewportWidth = size.width.toFloat().coerceAtLeast(1f)
                    val viewportHeight = size.height.toFloat().coerceAtLeast(1f)
                    val center = Offset(viewportWidth / 2f, viewportHeight / 2f)
                    val untransformed = Offset(
                        (position.x - center.x - pan.x) / zoom + center.x,
                        (position.y - center.y - pan.y) / zoom + center.y
                    )
                    val availableWidth = viewportWidth
                    val availableHeight = viewportHeight
                    val aspect = planBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (availableWidth / availableHeight)
                    val mapWidth = minOf(availableWidth, availableHeight * aspect)
                    val mapHeight = minOf(availableHeight, availableWidth / aspect)
                    val left = (viewportWidth - mapWidth) / 2f
                    val top = (viewportHeight - mapHeight) / 2f
                    return Offset((untransformed.x - left) / mapWidth, (untransformed.y - top) / mapHeight)
                }
                if (tool == MapTool.LINE || tool == MapTool.RECTANGLE || tool == MapTool.CIRCLE) {
                    var dragStart: Offset? = null
                    var dragEnd: Offset? = null
                    detectDragGestures(
                        onDragStart = { position ->
                            dragStart = mapPosition(position)
                            dragEnd = dragStart
                            onShapeDragPreview(dragStart, dragEnd)
                        },
                        onDrag = { change, _ ->
                            dragEnd = mapPosition(change.position)
                            onShapeDragPreview(dragStart, dragEnd)
                            change.consume()
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragEnd
                            if (start != null && end != null) onShapeDrag(start, end)
                            dragStart = null
                            dragEnd = null
                            clearShapePreview()
                        },
                        onDragCancel = {
                            dragStart = null
                            dragEnd = null
                            clearShapePreview()
                        }
                    )
                } else {
                    detectTapGestures { tap ->
                        mapPosition(tap)?.let(onMapTap)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            translationX = pan.x
            translationY = pan.y
            transformOrigin = TransformOrigin.Center
        }) {
            val availableWidth = size.width.coerceAtLeast(1f)
            val availableHeight = size.height.coerceAtLeast(1f)
            val aspect = planBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (availableWidth / availableHeight)
            val width = minOf(availableWidth, availableHeight * aspect)
            val height = minOf(availableHeight, availableWidth / aspect)
            val left = (size.width - width) / 2f
            val top = (size.height - height) / 2f
            drawRect(Color(0xFF090D19), Offset.Zero, Size(size.width, size.height))
            planBitmap?.let { bitmap ->
                drawIntoCanvas { canvas ->
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
                    canvas.nativeCanvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
                }
            }
            if (interpolationSamples.isNotEmpty()) {
                val columns = 32
                val rows = 20
                val cellW = width / columns
                val cellH = height / rows
                for (row in 0 until rows) for (col in 0 until columns) {
                    val px = (col + 0.5f) / columns
                    val py = (row + 0.5f) / rows
                    var weighted = 0f
                    var totalWeight = 0f
                    interpolationSamples.forEach { sample ->
                        val dx = px - sample.x
                        val dy = py - sample.y
                        val weight = 1f / (dx * dx + dy * dy + 0.012f)
                        weighted += sample.rssi * weight
                        totalWeight += weight
                    }
                    val value = weighted / totalWeight
                    val heat = when {
                        value >= -50f -> lerp(Color(0xFF69F5B5), Color(0xFF55F4FF), ((value + 50f) / 15f).coerceIn(0f, 1f))
                        value >= -65f -> lerp(Color(0xFFFFC857), Color(0xFF69F5B5), ((value + 65f) / 15f).coerceIn(0f, 1f))
                        else -> lerp(Color(0xFFFF5364), Color(0xFFFFC857), ((value + 85f) / 20f).coerceIn(0f, 1f))
                    }
                    drawRect(heat.copy(alpha = 0.48f), Offset(left + col * cellW, top + row * cellH), Size(cellW + 1f, cellH + 1f))
                }
            }
            val wall = HeatCyan.copy(alpha = 0.8f)
            sketchShapes.forEachIndexed { index, shape ->
                val p1 = Offset(left + width * shape.x1, top + height * shape.y1)
                val p2 = Offset(left + width * shape.x2, top + height * shape.y2)
                val shapeColor = if (index == selectedShapeIndex) Color(0xFFFFC857) else wall
                val shapeStroke = if (index == selectedShapeIndex) 5.dp.toPx() else 3.dp.toPx()
                when (shape.kind) {
                    SketchKind.LINE -> drawLine(shapeColor, p1, p2, strokeWidth = shapeStroke)
                    SketchKind.RECTANGLE -> drawRect(shapeColor, Offset(minOf(p1.x, p2.x), minOf(p1.y, p2.y)), Size(kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y)), style = Stroke(shapeStroke))
                    SketchKind.CIRCLE -> {
                        val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                        drawCircle(shapeColor, radius = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)) / 2f, center = center, style = Stroke(shapeStroke))
                    }
                }
            }
            val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(230, 85, 244, 255)
                textSize = 10.dp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            samples.forEachIndexed { index, sample ->
                val center = Offset(left + width * sample.x, top + height * sample.y)
                if (sample.time == selectedPointTime) drawCircle(Color(0xFFFFC857), radius = 16.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.75f), radius = 11.dp.toPx(), center = center)
                drawCircle(if (sample.time == selectedPointTime) Color(0xFFFFC857) else HeatCyan, radius = 8.dp.toPx(), center = center)
                drawCircle(Color(0xFF071018), radius = 4.dp.toPx(), center = center)
                labelPaint.color = android.graphics.Color.WHITE
                labelPaint.textSize = 9.dp.toPx()
                if (sample.time == selectedPointTime || index >= samples.lastIndex - 39) {
                    drawContext.canvas.nativeCanvas.drawText((index + 1).toString(), center.x + 10.dp.toPx(), center.y - 8.dp.toPx(), labelPaint)
                }
            }
            if (drawStart != null && drawPreviewEnd != null) {
                val start = Offset(left + width * drawStart.x, top + height * drawStart.y)
                val end = Offset(left + width * drawPreviewEnd.x, top + height * drawPreviewEnd.y)
                when (tool) {
                    MapTool.LINE -> drawLine(Color(0xFFFFC857), start, end, strokeWidth = 3.dp.toPx())
                    MapTool.RECTANGLE -> drawRect(Color(0xFFFFC857), Offset(minOf(start.x, end.x), minOf(start.y, end.y)), Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)), style = Stroke(3.dp.toPx()))
                    MapTool.CIRCLE -> {
                        val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                        drawCircle(Color(0xFFFFC857), radius = sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) / 2f, center = center, style = Stroke(3.dp.toPx()))
                    }
                    else -> Unit
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val halfLength = 9.dp.toPx()
            val crosshair = HeatCyan.copy(alpha = 0.9f)
            drawLine(crosshair, Offset(center.x - halfLength, center.y), Offset(center.x + halfLength, center.y), strokeWidth = 1.4.dp.toPx())
            drawLine(crosshair, Offset(center.x, center.y - halfLength), Offset(center.x, center.y + halfLength), strokeWidth = 1.4.dp.toPx())
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 1.8.dp.toPx(), center = center)
        }
    }
}

private fun distanceToShapeSquared(point: Offset, shape: SketchShape): Float {
    val start = Offset(shape.x1, shape.y1)
    val end = Offset(shape.x2, shape.y2)
    return when (shape.kind) {
        SketchKind.LINE -> distanceToSegmentSquared(point, start, end)
        SketchKind.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val right = maxOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val bottom = maxOf(start.y, end.y)
            minOf(
                distanceToSegmentSquared(point, Offset(left, top), Offset(right, top)),
                distanceToSegmentSquared(point, Offset(right, top), Offset(right, bottom)),
                distanceToSegmentSquared(point, Offset(right, bottom), Offset(left, bottom)),
                distanceToSegmentSquared(point, Offset(left, bottom), Offset(left, top))
            )
        }
        SketchKind.CIRCLE -> {
            val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
            val radius = sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) / 2f
            val radialDifference = (sqrt(distanceToSegmentSquared(point, center, center)) - radius).coerceAtLeast(0f)
            radialDifference * radialDifference
        }
    }
}

private fun distanceToShape(point: Offset, shape: SketchShape): Float = distanceToShapeSquared(point, shape)

private fun distanceToSegmentSquared(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    val projection = if (lengthSquared == 0f) 0f else (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val nearestX = start.x + projection * dx
    val nearestY = start.y + projection * dy
    val deltaX = point.x - nearestX
    val deltaY = point.y - nearestY
    return deltaX * deltaX + deltaY * deltaY
}

@Composable
private fun HeatLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(Modifier.size(10.dp), color = color, shape = RoundedCornerShape(3.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

private fun readSamples(serialized: String?): List<CoverageSample> {
    if (serialized.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(serialized)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(CoverageSample(
                    x = item.getDouble("x").toFloat(),
                    y = item.getDouble("y").toFloat(),
                    rssi = item.getInt("rssi").coerceIn(-100, 0),
                    bssid = item.optString("bssid", "BSSID недоступен"),
                    time = item.optLong("time")
                ))
            }
        }.takeLast(200)
    }.getOrDefault(emptyList())
}

private fun writeSamples(samples: List<CoverageSample>): String =
    JSONArray().apply {
        samples.forEach { sample ->
            put(JSONObject().apply {
                put("x", sample.x)
                put("y", sample.y)
                put("rssi", sample.rssi)
                put("bssid", sample.bssid)
                put("time", sample.time)
            })
        }
    }.toString()

private fun readShapes(serialized: String?): List<SketchShape> {
    if (serialized.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(serialized)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val kind = runCatching { SketchKind.valueOf(item.getString("kind")) }.getOrNull() ?: continue
                add(SketchShape(kind, item.getDouble("x1").toFloat(), item.getDouble("y1").toFloat(), item.getDouble("x2").toFloat(), item.getDouble("y2").toFloat()))
            }
        }
    }.getOrDefault(emptyList())
}

private fun writeShapes(shapes: List<SketchShape>): String = JSONArray().apply {
    shapes.forEach { shape ->
        put(JSONObject().apply {
            put("kind", shape.kind.name)
            put("x1", shape.x1); put("y1", shape.y1); put("x2", shape.x2); put("y2", shape.y2)
        })
    }
}.toString()

private fun decodePlanBitmap(context: Context, uri: android.net.Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height).toFloat())
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.RGB_565 }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

