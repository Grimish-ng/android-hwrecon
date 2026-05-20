package dev.hwrecon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ====
//  Design Tokens -- Industrial Dark
// ====

object ReconTheme {
    // Backgrounds
    val bg       = Color(0xFF0A0C0F)
    val bg1      = Color(0xFF0F1218)
    val bg2      = Color(0xFF141920)
    val bg3      = Color(0xFF1A2130)

    // Borders
    val border   = Color(0xFF1E2D3D)
    val border2  = Color(0xFF253447)

    // Accents
    val accent   = Color(0xFF00C8FF)   // cyan - primary
    val accent2  = Color(0xFFFF6B2B)   // orange - keys / node names
    val accent3  = Color(0xFFA8FF3E)   // green - values / OK
    val accent4  = Color(0xFFFF3E6C)   // red - errors

    // Text
    val text     = Color(0xFFC8D8E8)
    val text2    = Color(0xFF6A8099)
    val text3    = Color(0xFF3D5268)

    // Status
    val ok       = Color(0xFFA8FF3E)
    val warn     = Color(0xFFFFB830)
    val danger   = Color(0xFFFF3E6C)
    val info     = Color(0xFF00C8FF)

    // Typography
    val mono     = FontFamily.Monospace
}

// ====
//  Shared Components
// ====

/** Styled label in uppercase mono -- used for section headers and card labels. */
@Composable
fun Label(
    text: String,
    color: Color = ReconTheme.text3,
    fontSize: TextUnit = 9.sp,
    modifier: Modifier = Modifier,
) {
    Text(
        text       = text.uppercase(),
        color      = color,
        fontFamily = ReconTheme.mono,
        fontSize   = fontSize,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.12.sp,
        modifier   = modifier,
    )
}

/** Monospaced value text -- used inside data cards and table cells. */
@Composable
fun MonoText(
    text: String,
    color: Color = ReconTheme.text,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    Text(
        text       = text,
        color      = color,
        fontFamily = ReconTheme.mono,
        fontSize   = fontSize,
        fontWeight = fontWeight,
        modifier   = modifier,
    )
}

/** A single data card -- label over value with optional sub-text. */
@Composable
fun DataCard(
    label: String,
    value: String,
    sub: String? = null,
    valueColor: Color = ReconTheme.text,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(ReconTheme.bg2, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(label)
            MonoText(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (sub != null) {
                MonoText(sub, color = ReconTheme.text2, fontSize = 9.sp)
            }
        }
    }
}

/** Section header with a thin border below. */
@Composable
fun SectionHeader(
    title: String,
    path: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoText(title, color = ReconTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (path != null) {
                MonoText(path, color = ReconTheme.text3, fontSize = 9.sp)
            }
        }
        HorizontalDivider(color = ReconTheme.border, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
    }
}

/** Subsection label -- lighter divider above small uppercase label. */
@Composable
fun SubSection(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 7.dp)) {
        HorizontalDivider(color = ReconTheme.border, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
        Label(text, color = ReconTheme.text3, fontSize = 9.sp)
    }
}

/** Horizontal chip / tag. */
@Composable
fun Chip(
    text: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg     = if (highlighted) Color(0x1400C8FF) else ReconTheme.bg3
    val border = if (highlighted) Color(0x4000C8FF) else ReconTheme.border
    val color  = if (highlighted) ReconTheme.accent else ReconTheme.text2

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(2.dp))
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        MonoText(text, color = color, fontSize = 9.sp)
    }
}

/** Status indicator dot + text row. */
@Composable
fun StatusRow(
    message: String,
    ok: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReconTheme.bg2, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (ok) ReconTheme.ok else ReconTheme.danger, RoundedCornerShape(50))
        )
        MonoText(message, color = ReconTheme.text2, fontSize = 9.sp)
    }
}

/** A horizontally scrollable, syntax-highlighted code block. */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ReconTheme.bg1, RoundedCornerShape(4.dp))
            .border(1.dp, ReconTheme.border, RoundedCornerShape(4.dp))
            .padding(12.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        MonoText(text, color = ReconTheme.text2, fontSize = 9.sp)
    }
}

/** Simple bar chart row -- label / track / value. */
@Composable
fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    barColor: Color = ReconTheme.accent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoText(
            label,
            color = ReconTheme.text2,
            fontSize = 9.sp,
            modifier = Modifier.width(160.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .background(ReconTheme.bg3, RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
        MonoText(
            value,
            color = ReconTheme.text2,
            fontSize = 9.sp,
            modifier = Modifier.width(72.dp),
        )
    }
}

/** Badge chip with colour coding. */
enum class BadgeType { OK, WARN, ERROR, INFO }

@Composable
fun Badge(text: String, type: BadgeType = BadgeType.INFO) {
    val (bg, fg) = when (type) {
        BadgeType.OK    -> Color(0x1FA8FF3E) to ReconTheme.ok
        BadgeType.WARN  -> Color(0x1FFFB830) to ReconTheme.warn
        BadgeType.ERROR -> Color(0x1FFF3E6C) to ReconTheme.danger
        BadgeType.INFO  -> Color(0x1F00C8FF) to ReconTheme.info
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        MonoText(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Horizontal divider. */
@Composable
fun ReconDivider() = HorizontalDivider(color = ReconTheme.border, thickness = 1.dp)

/** Wrapping chip row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    chips: List<String>,
    highlightedChips: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            Chip(chip, highlighted = chip in highlightedChips)
        }
    }
}
