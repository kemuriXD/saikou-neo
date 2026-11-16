package ani.saikou.neo.phone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.pairing.PairCodeState
import ani.saikou.neo.shared.pairing.TV_CODE_LENGTH
import ani.saikou.neo.shared.pairing.normalizeTvCode

/**
 * 3.3 Pair: type the code. Six character cells sit over one real text field (#72), so the keyboard, paste and TalkBack
 * all work. [PairCodeState] holds the code, already tidied into capitals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairCodeScreen(
    state: PairCodeState,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    val animate = rememberAnimationsOn()
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.pair_type_title), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.icon_arrow_back), contentDescription = stringResource(R.string.pair_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PhoneColumn(Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                Text(
                    stringResource(R.string.pair_type_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                CodeField(state.code, onCodeChange, onConnect = { if (state.complete) onConnect() }, animate)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.pair_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                ConnectButton(state.complete, onConnect, animate)
            }
        }
    }
}

@Composable
private fun CodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    animate: Boolean,
) {
    val focus = remember { FocusRequester() }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val label = stringResource(R.string.pair_code_field)
    LaunchedEffect(focus) { focus.requestFocus() }
    // The field tidies each edit as it lands, before the next key, so a fast typist or a paste sent as keys loses
    // nothing. It starts from the screen's code, so the code comes back after the phone is turned.
    val field = rememberTextFieldState(code)
    val text = field.text.toString()
    val count = pluralStringResource(R.plurals.pair_code_count, text.length, text.length, TV_CODE_LENGTH)
    val latest by rememberUpdatedState(onCodeChange)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { latest(it) } }

    BasicTextField(
        state = field,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics {
                    contentDescription = label
                    stateDescription = count
                    liveRegion = LiveRegionMode.Polite
                },
        inputTransformation = TidyCode,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Go,
            ),
        onKeyboardAction = { onConnect() },
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactions,
        cursorBrush = SolidColor(PhoneColors.pink.copy(alpha = 0f)),
        decorator = { innerField ->
            Box {
                // The real field stays underneath, invisible; the cells draw what it holds.
                Box(Modifier.alpha(0f)) { innerField() }
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(TV_CODE_LENGTH) { index ->
                        CodeCell(text.getOrNull(index), focused = focused && index == text.length, animate = animate)
                    }
                }
            }
        },
    )
}

/** Keeps only what [normalizeTvCode] allows, with the caret at the end, since the cells can't show it anywhere else. */
private val TidyCode =
    InputTransformation {
        val tidy = normalizeTvCode(toString())
        if (tidy != toString()) replace(0, length, tidy)
        placeCursorAtEnd()
    }

/** One cell: empty on ink, the next one with a pink border and caret, a typed one on surface. */
@Composable
private fun CodeCell(
    char: Char?,
    focused: Boolean,
    animate: Boolean,
) {
    val border by animateColorAsState(
        if (focused) PhoneColors.pink else PhoneColors.outline,
        animationSpec = if (animate) tween(BORDER_FADE_MILLIS) else snap(),
        label = "cell border",
    )
    Box(
        Modifier
            .width(52.dp)
            .height(64.dp)
            .background(if (char != null || focused) PhoneColors.surface else PhoneColors.ink, CellShape)
            .border(if (focused) 2.dp else 1.dp, border, CellShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            char != null -> TypedChar(char, animate)
            focused -> Caret(animate)
        }
    }
}

/** A typed character grows from 82% and fades in over 160 ms. */
@Composable
private fun TypedChar(
    char: Char,
    animate: Boolean,
) {
    val appear = remember(char) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(appear) { appear.animateTo(1f, tween(CHAR_IN_MILLIS)) }
    Text(
        char.toString(),
        style = MaterialTheme.typography.headlineSmall,
        modifier =
            Modifier.graphicsLayer {
                val grow = CHAR_START_SCALE + (1f - CHAR_START_SCALE) * appear.value
                scaleX = grow
                scaleY = grow
                alpha = appear.value
            },
    )
}

/** The pink caret blinks once a second, or stays solid with animations off. */
@Composable
private fun Caret(animate: Boolean) {
    val visible =
        if (animate) {
            val blink by rememberInfiniteTransition(label = "caret").animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 0, delayMillis = CARET_HALF_MILLIS), RepeatMode.Reverse),
                label = "caret blink",
            )
            blink
        } else {
            1f
        }
    Box(Modifier.alpha(visible).size(width = 2.dp, height = 28.dp).background(PhoneColors.pink, RoundedCornerShape(1.dp)))
}

/** Disabled until all six are in, then primary with a 200 ms settle from 96%. */
@Composable
private fun ConnectButton(
    enabled: Boolean,
    onConnect: () -> Unit,
    animate: Boolean,
) {
    val settle = remember { Animatable(1f) }
    LaunchedEffect(enabled) {
        if (enabled && animate) {
            settle.snapTo(CONNECT_START_SCALE)
            settle.animateTo(1f, tween(CONNECT_SETTLE_MILLIS))
        }
    }
    Button(
        onClick = onConnect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).scale(settle.value),
    ) {
        Text(stringResource(R.string.pair_connect), style = MaterialTheme.typography.titleMedium)
    }
}

private val CellShape = RoundedCornerShape(12.dp)
private const val CHAR_START_SCALE = 0.82f
private const val CHAR_IN_MILLIS = 160
private const val BORDER_FADE_MILLIS = 300
private const val CONNECT_START_SCALE = 0.96f
private const val CONNECT_SETTLE_MILLIS = 200
private const val CARET_HALF_MILLIS = 500
