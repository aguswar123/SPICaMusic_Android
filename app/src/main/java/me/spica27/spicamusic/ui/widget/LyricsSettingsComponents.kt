package me.spica27.spicamusic.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Section title such as "FONT" or "TEXT SIZE". */
@Composable
internal fun LyricsSettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/** Label + short description shown above a control. */
@Composable
private fun LabelBlock(
    label: String,
    description: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (description.isNotEmpty()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Slider over an Int range. The thumb follows the finger smoothly, and [onValueChange] is called
 * with a value already snapped to [step] on every change (the lyrics update live while dragging).
 */
@Composable
internal fun LyricsSettingsSlider(
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueText: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: value.toFloat()
    val snapped = (shown / step).roundToInt() * step
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LabelBlock(label = label, description = description, trailing = valueText(snapped.coerceIn(range)))
        Slider(
            value = shown,
            onValueChange = {
                dragValue = it
                val v = ((it / step).roundToInt() * step).coerceIn(range)
                if (v != value) onValueChange(v)
            },
            onValueChangeFinished = { dragValue = null },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        )
    }
}

/** Row of selectable chips (single choice). Each chip can use its own font to preview it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> LyricsSettingsChips(
    label: String,
    description: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    optionFont: (T) -> FontFamily? = { null },
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LabelBlock(label = label, description = description)
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = optionLabel(option),
                            style = TextStyle(fontFamily = optionFont(option)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

/** Label + switch. Used by the sections that come with the next parts. */
@Composable
internal fun LyricsSettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LabelBlock(label = label, description = description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun LyricsSettingsResetButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) { Text(text) }
}
