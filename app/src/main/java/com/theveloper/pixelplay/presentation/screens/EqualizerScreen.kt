package com.theveloper.pixelplay.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState // Added
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset // Added
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize // Added
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // Added
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale // Added
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // Added
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
 // Added
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.viewmodel.EqualizerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.Check // Added import for Switch check icon
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import com.theveloper.pixelplay.presentation.components.WavyArcSlider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Edit // Added
import androidx.compose.material.icons.rounded.ExpandMore // Added
import androidx.compose.material.icons.rounded.Save // Added
import androidx.compose.material.icons.filled.Star // Added
import androidx.compose.material3.Surface
import com.theveloper.pixelplay.presentation.components.CustomPresetsSheet
import com.theveloper.pixelplay.presentation.components.ReorderPresetsSheet
import com.theveloper.pixelplay.presentation.components.SavePresetDialog
import com.theveloper.pixelplay.presentation.components.RenamePresetDialog
import com.theveloper.pixelplay.data.preferences.EqualizerViewMode
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ViewQuilt
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()

    // Sheet States
    var showCustomPresetsSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<EqualizerPreset?>(null) }
    
    // Handlers
    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name -> equalizerViewModel.saveCurrentAsCustomPreset(name) }
        )
    }
    
    renameTarget?.let { preset ->
        RenamePresetDialog(
            currentName = preset.displayName,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                equalizerViewModel.renameCustomPreset(preset.name, newName)
            }
        )
    }
    
    if (showCustomPresetsSheet) {
        CustomPresetsSheet(
            presets = uiState.customPresets,
            pinnedPresetsNames = uiState.pinnedPresetsNames,
            onPresetSelected = { equalizerViewModel.selectPreset(it) },
            onPinToggled = { equalizerViewModel.togglePinPreset(it.name) },
            onRename = { renameTarget = it },
            onDelete = { equalizerViewModel.deleteCustomPreset(it) },
            onDismiss = { showCustomPresetsSheet = false }
        )
    }
    
    ReorderPresetsSheet(
        visible = showReorderSheet,
        allAvailablePresets = uiState.allAvailablePresets,
        pinnedPresetsNames = uiState.pinnedPresetsNames,
        onSave = { newOrder -> equalizerViewModel.updatePinnedPresetsOrder(newOrder) },
        onReset = { equalizerViewModel.resetPinnedPresetsToDefault() },
        onDismiss = { showReorderSheet = false }
    )
    
    // Transition animations
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }
    
    val transition = rememberTransition(transitionState, label = "EqualizerAppearTransition")
    
    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }
    
    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }
    
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }
                
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight
                
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }
                
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }
    
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
            ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Preset Tabs
            item(key = "preset_tabs") {
                val visiblePresets = remember(uiState.accessiblePresets) {
                    val defaultPresets = uiState.accessiblePresets.filter { !it.isCustom }
                    defaultPresets + EqualizerPreset.custom(List(10) { 0 }) // Always show "Custom" tab at end
                }
                
                PresetTabsRow(
                    presets = visiblePresets,
                    selectedPreset = uiState.currentPreset,
                    onPresetSelected = {
                        equalizerViewModel.selectPreset(it) 
                    },
                    onEditClick = { showReorderSheet = true }
                )
            }
            
            // Band Sliders
            item(key = "band_sliders") {
                BandSlidersSection(
                    bandLevels = uiState.bandLevels,
                    isEnabled = uiState.isEnabled,
                    currentPreset = uiState.currentPreset,
                    editingPresetName = uiState.editingPresetName,
                    onBandLevelChanged = { bandId, level ->
                        equalizerViewModel.setBandLevel(bandId, level)
                    },
                    viewMode = uiState.viewMode,
                    onSaveClick = { showSaveDialog = true },
                    onUpdateClick = {
                        uiState.editingPresetName?.let { equalizerViewModel.updateCustomPresetBands(it) }
                    },
                    onPresetsListClick = { showCustomPresetsSheet = true },
                    onUnpinClick = { }
                )
            }
            
            // Effect Controls
            item(key = "effect_controls") {
                EffectControlsSection(
                    bassBoostEnabled = uiState.bassBoostEnabled,
                    bassBoostStrength = uiState.bassBoostStrength, // Now Float
                    virtualizerEnabled = uiState.virtualizerEnabled,
                    virtualizerStrength = uiState.virtualizerStrength, // Now Float
                    loudnessEnabled = uiState.loudnessEnhancerEnabled,
                    loudnessStrength = uiState.loudnessEnhancerStrength, // Now Float
                    isBassBoostSupported = uiState.isBassBoostSupported,
                    isVirtualizerSupported = uiState.isVirtualizerSupported,
                    isLoudnessEnhancerSupported = uiState.isLoudnessEnhancerSupported,
                    isBassBoostDismissed = uiState.isBassBoostDismissed,
                    isVirtualizerDismissed = uiState.isVirtualizerDismissed,
                    isLoudnessDismissed = uiState.isLoudnessDismissed,
                    onBassBoostEnabledChange = { equalizerViewModel.setBassBoostEnabled(it) },
                    onBassBoostStrengthChange = { equalizerViewModel.setBassBoostStrength(it.roundToInt()) },
                    onVirtualizerEnabledChange = { equalizerViewModel.setVirtualizerEnabled(it) },
                    onVirtualizerStrengthChange = { equalizerViewModel.setVirtualizerStrength(it.roundToInt()) },
                    onLoudnessEnabledChange = { equalizerViewModel.setLoudnessEnhancerEnabled(it) },
                    onLoudnessStrengthChange = { equalizerViewModel.setLoudnessEnhancerStrength(it.roundToInt()) },
                    onDismissBassBoost = { equalizerViewModel.setBassBoostDismissed(true) },
                    onDismissVirtualizer = { equalizerViewModel.setVirtualizerDismissed(true) },
                    onDismissLoudness = { equalizerViewModel.setLoudnessDismissed(true) }
                )
            }

            // DynamicBass Controls
            item(key = "dynamic_bass_controls") {
                DynamicBassControlsSection(
                    dynamicBassEnabled = uiState.dynamicBassEnabled,
                    dynamicBassBassGain = uiState.dynamicBassBassGain,
                    dynamicBassFilterXLow = uiState.dynamicBassFilterXLow,
                    dynamicBassFilterXHigh = uiState.dynamicBassFilterXHigh,
                    dynamicBassFilterYLow = uiState.dynamicBassFilterYLow,
                    dynamicBassFilterYHigh = uiState.dynamicBassFilterYHigh,
                    dynamicBassSideGainX = uiState.dynamicBassSideGainX,
                    dynamicBassSideGainY = uiState.dynamicBassSideGainY,
                    onDynamicBassEnabledChange = { equalizerViewModel.setDynamicBassEnabled(it) },
                    onBassGainChange = { equalizerViewModel.setDynamicBassBassGain(it) },
                    onFilterXChange = { low, high -> equalizerViewModel.setDynamicBassFilterX(low, high) },
                    onFilterYChange = { low, high -> equalizerViewModel.setDynamicBassFilterY(low, high) },
                    onSideGainChange = { gx, gy -> equalizerViewModel.setDynamicBassSideGain(gx, gy) }
                )
            }
            
            // Volume Control
            item(key = "volume_control") {
                val volume by equalizerViewModel.systemVolume.collectAsStateWithLifecycle()
                VolumeControlCard(
                    volume = volume,
                    onVolumeChange = { equalizerViewModel.setSystemVolume(it) }
                )
            }
        }
        
        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_category_equalizer_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = { navController.popBackStack() },
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 72.dp,
            actions = {
                // View Mode Toggle
                FilledIconButton(
                    onClick = { equalizerViewModel.cycleViewMode() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = when(uiState.viewMode) {
                            EqualizerViewMode.SLIDERS -> Icons.Rounded.GraphicEq
                            EqualizerViewMode.GRAPH -> Icons.AutoMirrored.Rounded.ShowChart
                            EqualizerViewMode.HYBRID -> Icons.AutoMirrored.Rounded.ViewQuilt
                        },
                        contentDescription = stringResource(R.string.equalizer_change_view_mode_cd)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Power toggle
                val isEnabled = uiState.isEnabled
                val powerButtonCorner by animateIntAsState(
                    targetValue = if (isEnabled) 50 else 12,
                    label = "PowerButtonShape"
                )

                FilledIconToggleButton(
                    checked = isEnabled,
                    onCheckedChange = { equalizerViewModel.toggleEqualizer() },
                    shape = RoundedCornerShape(powerButtonCorner),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (isEnabled) {
                            stringResource(R.string.equalizer_disable_cd)
                        } else {
                            stringResource(R.string.equalizer_enable_cd)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
            }
        )
    }
}

@Composable
private fun DynamicBassControlsSection(
    dynamicBassEnabled: Boolean,
    dynamicBassBassGain: Float,
    dynamicBassFilterXLow: Float,
    dynamicBassFilterXHigh: Float,
    dynamicBassFilterYLow: Float,
    dynamicBassFilterYHigh: Float,
    dynamicBassSideGainX: Float,
    dynamicBassSideGainY: Float,
    onDynamicBassEnabledChange: (Boolean) -> Unit,
    onBassGainChange: (Float) -> Unit,
    onFilterXChange: (Float, Float) -> Unit,
    onFilterYChange: (Float, Float) -> Unit,
    onSideGainChange: (Float, Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title and Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dynamic Bass",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = dynamicBassEnabled,
                    onCheckedChange = onDynamicBassEnabledChange,
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (dynamicBassEnabled) {
                // Bass Gain Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bass Gain",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${(dynamicBassBassGain * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = dynamicBassBassGain,
                        onValueChange = onBassGainChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Filter X Range Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filter X (Hz)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${dynamicBassFilterXLow.toInt()}-${dynamicBassFilterXHigh.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Low slider
                        Slider(
                            value = dynamicBassFilterXLow,
                            onValueChange = { newLow ->
                                if (newLow < dynamicBassFilterXHigh) {
                                    onFilterXChange(newLow, dynamicBassFilterXHigh)
                                }
                            },
                            valueRange = 20f..500f,
                            modifier = Modifier.weight(1f)
                        )
                        // High slider
                        Slider(
                            value = dynamicBassFilterXHigh,
                            onValueChange = { newHigh ->
                                if (newHigh > dynamicBassFilterXLow) {
                                    onFilterXChange(dynamicBassFilterXLow, newHigh)
                                }
                            },
                            valueRange = 20f..500f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Filter Y Range Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filter Y (Hz)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "${dynamicBassFilterYLow.toInt()}-${dynamicBassFilterYHigh.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Low slider
                        Slider(
                            value = dynamicBassFilterYLow,
                            onValueChange = { newLow ->
                                if (newLow < dynamicBassFilterYHigh) {
                                    onFilterYChange(newLow, dynamicBassFilterYHigh)
                                }
                            },
                            valueRange = 20f..500f,
                            modifier = Modifier.weight(1f)
                        )
                        // High slider
                        Slider(
                            value = dynamicBassFilterYHigh,
                            onValueChange = { newHigh ->
                                if (newHigh > dynamicBassFilterYLow) {
                                    onFilterYChange(dynamicBassFilterYLow, newHigh)
                                }
                            },
                            valueRange = 20f..500f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Side Gain X & Y
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Side Gain X
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Side Gain X",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${(dynamicBassSideGainX * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = dynamicBassSideGainX,
                            onValueChange = { newGx ->
                                onSideGainChange(newGx, dynamicBassSideGainY)
                            },
                            valueRange = -1f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Side Gain Y
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Side Gain Y",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${(dynamicBassSideGainY * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = dynamicBassSideGainY,
                            onValueChange = { newGy ->
                                onSideGainChange(dynamicBassSideGainX, newGy)
                            },
                            valueRange = -1f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
