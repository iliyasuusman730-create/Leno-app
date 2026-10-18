package com.example.ui.components.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KeyboardTab(val label: String) {
    ABC("ABC"),
    MATH("Math"),
    CHEM("Chem"),
    PHYSICS("Physics"),
    QURAN("Quran"),
    ONLINE_SKILLS("Online Skills"),
    DRAW("Draw")
}

data class KeyItem(
    val label: String,
    val value: String = label,
    val color: Color = Color(0xFF27272A),
    val textColor: Color = Color.White,
    val isAction: Boolean = false
)

@Composable
fun LenoKeyboard(
    onInsertText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    // Keyboard container: fixed height 42% of screen (not 60%), background #121212
    val keyboardHeight = screenHeight * 0.42f

    var activeTab by remember { mutableStateOf(KeyboardTab.MATH) }
    var currentFormula by remember { mutableStateOf("∫ (x² + ½) dx = ") }
    var isShiftActive by remember { mutableStateOf(false) }

    fun selectTab(tab: KeyboardTab) {
        activeTab = tab
        currentFormula = when (tab) {
            KeyboardTab.MATH -> "∫ (x² + ½) dx = "
            KeyboardTab.CHEM -> "CO₂ + H₂O → "
            KeyboardTab.PHYSICS -> "F = m × a"
            KeyboardTab.QURAN -> "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
            KeyboardTab.ONLINE_SKILLS -> "HTML • CSS • React • Python • AI Tools"
            KeyboardTab.ABC -> ""
            KeyboardTab.DRAW -> "Drawing mode"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(keyboardHeight)
            .testTag("leno_custom_keyboard"),
        color = Color(0xFF121212), // Background #121212
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === FORMULA PREVIEW BAR: height 45px fixed, hidden for ABC ===
            val isFormulaBarVisible = activeTab != KeyboardTab.ABC
            if (isFormulaBarVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp) // Formula bar height: 45px fixed
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("formula_preview_bar"),
                    color = Color(0xFF09090B),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Formula: ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E)
                        )
                        Text(
                            text = currentFormula,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Clear formula preview
                        if (currentFormula.isNotEmpty()) {
                            IconButton(
                                onClick = { currentFormula = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Formula",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Insert button: ALWAYS visible, not cut off
                        Button(
                            onClick = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("keyboard_insert_button")
                        ) {
                            Text("Insert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // === TABS ROW: 40px fixed (ABC, Math, Chem, Physics, Quran, Online Skills, Draw) ===
            val tabs = listOf(
                KeyboardTab.ABC,
                KeyboardTab.MATH,
                KeyboardTab.CHEM,
                KeyboardTab.PHYSICS,
                KeyboardTab.QURAN,
                KeyboardTab.ONLINE_SKILLS,
                KeyboardTab.DRAW
            )
            val selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF18181B),
                contentColor = Color.White,
                edgePadding = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp) // Tabs row: 40px fixed
                    .testTag("keyboard_tabs_row"),
                indicator = { tabPositions ->
                    if (selectedTabIndex in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color(0xFF22C55E),
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF27272A)))
                }
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == activeTab
                    Tab(
                        selected = isSelected,
                        onClick = { selectTab(tab) },
                        modifier = Modifier.height(40.dp),
                        text = {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF22C55E) else Color(0xFFA1A1AA)
                            )
                        }
                    )
                }
            }

            // === KEYS AREA: 4 columns, each key height 48px, gap 6px, padding bottom 20px, scrollable ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    KeyboardTab.ABC -> {
                        AbcKeyboardContent(
                            isShift = isShiftActive,
                            onShiftToggle = { isShiftActive = !isShiftActive },
                            onKeyClick = { key ->
                                onInsertText(key)
                            },
                            onBackspace = onBackspace,
                            onEnter = onEnter,
                            onSpace = { onInsertText(" ") }
                        )
                    }
                    KeyboardTab.MATH -> {
                        FourColumnKeyGrid(
                            items = mathKeyItems,
                            onKeyClick = { key ->
                                currentFormula += key
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                    KeyboardTab.CHEM -> {
                        FourColumnKeyGrid(
                            items = chemKeyItems,
                            onKeyClick = { key ->
                                currentFormula += key
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                    KeyboardTab.PHYSICS -> {
                        FourColumnKeyGrid(
                            items = physicsKeyItems,
                            onKeyClick = { key ->
                                currentFormula += key
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                    KeyboardTab.QURAN -> {
                        FourColumnKeyGrid(
                            items = quranKeyItems,
                            onKeyClick = { key ->
                                currentFormula += key
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                    KeyboardTab.ONLINE_SKILLS -> {
                        FourColumnKeyGrid(
                            items = onlineSkillsKeyItems,
                            onKeyClick = { key ->
                                currentFormula += if (currentFormula.isEmpty() || currentFormula.endsWith(" ")) key else " $key"
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                    KeyboardTab.DRAW -> {
                        FourColumnKeyGrid(
                            items = drawKeyItems,
                            onKeyClick = { key ->
                                currentFormula += key
                            },
                            onInsert = {
                                if (currentFormula.isNotEmpty()) {
                                    onInsertText(currentFormula)
                                }
                            },
                            onBackspace = {
                                if (currentFormula.isNotEmpty()) {
                                    currentFormula = currentFormula.dropLast(1)
                                } else {
                                    onBackspace()
                                }
                            },
                            onClear = { currentFormula = "" },
                            onSpace = { currentFormula += " " }
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4-Column Key Grid: Height 48px, Gap 6px, Bottom Padding 20px, Vertical Scroll
// -----------------------------------------------------------------------------
@Composable
private fun FourColumnKeyGrid(
    items: List<KeyItem>,
    onKeyClick: (String) -> Unit,
    onInsert: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 20.dp), // bottom padding 20px for home indicator
        verticalArrangement = Arrangement.spacedBy(6.dp) // gap 6px
    ) {
        // Quick Action Row pinned at top of grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyButton(
                label = "␣ Space",
                modifier = Modifier.weight(1f).height(48.dp),
                containerColor = Color(0xFF27272A)
            ) { onSpace() }

            KeyButton(
                label = "CLR",
                modifier = Modifier.weight(1f).height(48.dp),
                containerColor = Color(0xFF7F1D1D)
            ) { onClear() }

            KeyButton(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f).height(48.dp),
                containerColor = Color(0xFF3F3F46)
            ) { onBackspace() }

            KeyButton(
                label = "↵ Insert",
                modifier = Modifier.weight(1f).height(48.dp),
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White
            ) { onInsert() }
        }

        // Keys chunked into exactly 4 columns
        val rows = items.chunked(4)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp) // gap 6px
            ) {
                rowItems.forEach { item ->
                    KeyButton(
                        label = item.label,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp), // each key height 48px
                        containerColor = item.color,
                        contentColor = item.textColor
                    ) {
                        onKeyClick(item.value)
                    }
                }
                // Fill remaining columns if less than 4
                for (i in 0 until (4 - rowItems.size)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Key Data Sets
// -----------------------------------------------------------------------------
private val mathKeyItems = listOf(
    // Row 1: Basic ops
    KeyItem("+"), KeyItem("−"), KeyItem("×"), KeyItem("÷"),
    // Row 2: Equalities
    KeyItem("="), KeyItem("≠"), KeyItem("≤"), KeyItem("≥"),
    // Row 3: Roots & Constants
    KeyItem("√"), KeyItem("∛"), KeyItem("π"), KeyItem("∞"),
    // Row 4: Calculus
    KeyItem("∫"), KeyItem("∂"), KeyItem("∑"), KeyItem("∏"),
    // Row 5: Powers
    KeyItem("x²", "²"), KeyItem("x³", "³"), KeyItem("xⁿ", "ⁿ"), KeyItem("x⁰", "⁰"),
    // Row 6: Powers 2
    KeyItem("x⁺", "⁺"), KeyItem("x⁻", "⁻"), KeyItem("x¹", "¹"), KeyItem("x⁴", "⁴"),
    // Row 7: Subscripts
    KeyItem("x₀", "₀"), KeyItem("x₁", "₁"), KeyItem("x₂", "₂"), KeyItem("x₃", "₃"),
    // Row 8: Subscripts 2
    KeyItem("x₄", "₄"), KeyItem("xᵢ", "ᵢ"), KeyItem("xₙ", "ₙ"), KeyItem("dx"),
    // Row 9: Functions
    KeyItem("sin"), KeyItem("cos"), KeyItem("tan"), KeyItem("log"),
    // Row 10: Functions 2
    KeyItem("ln"), KeyItem("lim"), KeyItem("Δ"), KeyItem("θ"),
    // Row 11: Brackets & relations
    KeyItem("("), KeyItem(")"), KeyItem("°"), KeyItem("%"),
    // Row 12: Relations
    KeyItem("±"), KeyItem("≈"), KeyItem("∈"), KeyItem("∉")
)

private val chemKeyItems = listOf(
    // Common Molecules - all requested: H₂O, CO, NaCl, etc.
    KeyItem("H₂O", color = Color(0xFF0F766E)),
    KeyItem("CO₂", color = Color(0xFF0F766E)),
    KeyItem("CO", color = Color(0xFF0F766E)),
    KeyItem("NaCl", color = Color(0xFF0F766E)),

    KeyItem("HCl", color = Color(0xFF0F766E)),
    KeyItem("H₂SO₄", color = Color(0xFF0F766E)),
    KeyItem("NaOH", color = Color(0xFF0F766E)),
    KeyItem("CH₄", color = Color(0xFF0F766E)),

    KeyItem("O₂", color = Color(0xFF0F766E)),
    KeyItem("N₂", color = Color(0xFF0F766E)),
    KeyItem("C₆H₁₂O₆", color = Color(0xFF0F766E)),
    KeyItem("CaCO₃", color = Color(0xFF0F766E)),

    // Subscript Numbers
    KeyItem("₀", color = Color(0xFF1E293B)),
    KeyItem("₁", color = Color(0xFF1E293B)),
    KeyItem("₂", color = Color(0xFF1E293B)),
    KeyItem("₃", color = Color(0xFF1E293B)),

    KeyItem("₄", color = Color(0xFF1E293B)),
    KeyItem("₅", color = Color(0xFF1E293B)),
    KeyItem("₆", color = Color(0xFF1E293B)),
    KeyItem("₇", color = Color(0xFF1E293B)),

    KeyItem("₈", color = Color(0xFF1E293B)),
    KeyItem("₉", color = Color(0xFF1E293B)),
    KeyItem("+", color = Color(0xFF27272A)),
    KeyItem("−", color = Color(0xFF27272A)),

    // Reactions & States
    KeyItem("→", " → ", color = Color(0xFF14532D)),
    KeyItem("⇌", " ⇌ ", color = Color(0xFF14532D)),
    KeyItem("↑", "↑", color = Color(0xFF14532D)),
    KeyItem("↓", "↓", color = Color(0xFF14532D)),

    KeyItem("(s)", color = Color(0xFF1F2937)),
    KeyItem("(l)", color = Color(0xFF1F2937)),
    KeyItem("(g)", color = Color(0xFF1F2937)),
    KeyItem("(aq)", color = Color(0xFF1F2937)),

    // Elements
    KeyItem("H"), KeyItem("C"), KeyItem("N"), KeyItem("O"),
    KeyItem("Na"), KeyItem("Cl"), KeyItem("Ca"), KeyItem("Fe"),
    KeyItem("Mg"), KeyItem("Al"), KeyItem("P"), KeyItem("S"),
    KeyItem("K"), KeyItem("Cu"), KeyItem("Zn"), KeyItem("Ag"),

    // Charges & Radicals
    KeyItem("⁺", color = Color(0xFF1E1B4B)),
    KeyItem("⁻", color = Color(0xFF1E1B4B)),
    KeyItem("²⁺", color = Color(0xFF1E1B4B)),
    KeyItem("²⁻", color = Color(0xFF1E1B4B)),

    KeyItem("³⁺", color = Color(0xFF1E1B4B)),
    KeyItem("OH⁻", color = Color(0xFF1E1B4B)),
    KeyItem("SO₄²⁻", color = Color(0xFF1E1B4B)),
    KeyItem("NO₃⁻", color = Color(0xFF1E1B4B))
)

private val physicsKeyItems = listOf(
    // Greek Symbols
    KeyItem("α", color = Color(0xFF312E81)),
    KeyItem("β", color = Color(0xFF312E81)),
    KeyItem("γ", color = Color(0xFF312E81)),
    KeyItem("θ", color = Color(0xFF312E81)),

    KeyItem("λ", color = Color(0xFF312E81)),
    KeyItem("μ", color = Color(0xFF312E81)),
    KeyItem("ω", color = Color(0xFF312E81)),
    KeyItem("Ω", color = Color(0xFF312E81)),

    // Units
    KeyItem("m/s", color = Color(0xFF1E293B)),
    KeyItem("m/s²", color = Color(0xFF1E293B)),
    KeyItem("kg", color = Color(0xFF1E293B)),
    KeyItem("N", color = Color(0xFF1E293B)),

    KeyItem("J", color = Color(0xFF1E293B)),
    KeyItem("W", color = Color(0xFF1E293B)),
    KeyItem("Pa", color = Color(0xFF1E293B)),
    KeyItem("Hz", color = Color(0xFF1E293B)),

    KeyItem("V", color = Color(0xFF1E293B)),
    KeyItem("A", color = Color(0xFF1E293B)),
    KeyItem("K", color = Color(0xFF1E293B)),
    KeyItem("°C", color = Color(0xFF1E293B)),

    // Vectors
    KeyItem("→v", color = Color(0xFF1F2937)),
    KeyItem("→F", color = Color(0xFF1F2937)),
    KeyItem("→a", color = Color(0xFF1F2937)),
    KeyItem("→p", color = Color(0xFF1F2937)),

    // Standard Formulas
    KeyItem("E = mc²", color = Color(0xFF065F46)),
    KeyItem("F = ma", color = Color(0xFF065F46)),
    KeyItem("V = IR", color = Color(0xFF065F46)),
    KeyItem("P = IV", color = Color(0xFF065F46)),

    KeyItem("v = u+at", color = Color(0xFF065F46)),
    KeyItem("KE = ½mv²", color = Color(0xFF065F46)),
    KeyItem("p = mv", color = Color(0xFF065F46)),
    KeyItem("PV = nRT", color = Color(0xFF065F46)),

    KeyItem("g", color = Color(0xFF27272A)),
    KeyItem("c", color = Color(0xFF27272A)),
    KeyItem("Δt", color = Color(0xFF27272A)),
    KeyItem("Δx", color = Color(0xFF27272A))
)

private val drawKeyItems = listOf(
    // Circuits
    KeyItem("Resistor", "[circuit: resistor]", color = Color(0xFF27272A)),
    KeyItem("Battery", "[circuit: battery 12V]", color = Color(0xFF27272A)),
    KeyItem("Switch", "[circuit: switch]", color = Color(0xFF27272A)),
    KeyItem("Ground", "[circuit: ground ⏚]", color = Color(0xFF27272A)),

    KeyItem("Capacitor", "[circuit: capacitor]", color = Color(0xFF27272A)),
    KeyItem("Diode", "[circuit: diode]", color = Color(0xFF27272A)),
    KeyItem("Bulb", "[circuit: bulb]", color = Color(0xFF27272A)),
    KeyItem("Wire", "────", color = Color(0xFF27272A)),

    // Optics & Geometry
    KeyItem("Ray ──▷", "──▷", color = Color(0xFF1E293B)),
    KeyItem("Angle ∠", "∠", color = Color(0xFF1E293B)),
    KeyItem("Perp ⊥", "⊥", color = Color(0xFF1E293B)),
    KeyItem("Parallel ∥", "∥", color = Color(0xFF1E293B)),

    KeyItem("Triangle △", "△ABC", color = Color(0xFF1E293B)),
    KeyItem("Circle ◯", "◯ (radius r)", color = Color(0xFF1E293B)),
    KeyItem("Convex Lens", "[optics: convex lens]", color = Color(0xFF1E293B)),
    KeyItem("Mirror", "[optics: plane mirror]", color = Color(0xFF1E293B))
)

private val quranKeyItems = listOf(
    // Common Quranic Words & Phrases
    KeyItem("بِسْمِ اللَّهِ", "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", color = Color(0xFF065F46)),
    KeyItem("الْحَمْدُ لِلَّهِ", color = Color(0xFF065F46)),
    KeyItem("سُبْحَانَ اللَّهِ", color = Color(0xFF065F46)),
    KeyItem("اللَّهُ أَكْبَرُ", color = Color(0xFF065F46)),

    KeyItem("مَاشَاءَ اللَّهُ", color = Color(0xFF065F46)),
    KeyItem("صَلَّى اللَّهُ", "صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ", color = Color(0xFF065F46)),
    KeyItem("آية ۝", " ۝ ", color = Color(0xFF166534)),
    KeyItem("سورة", "سورة ", color = Color(0xFF166534)),

    // Tajweed & Markings
    KeyItem("تَجْوِيدْ", color = Color(0xFF1E293B)),
    KeyItem("إِدْغَامْ", color = Color(0xFF1E293B)),
    KeyItem("إِظْهَارْ", color = Color(0xFF1E293B)),
    KeyItem("إِخْفَاءْ", color = Color(0xFF1E293B)),

    KeyItem("قَلْقَلَة", color = Color(0xFF1E293B)),
    KeyItem("مَدّ ٓ", " ٓ ", color = Color(0xFF1E293B)),
    KeyItem("وَقْف ۘ", " ۘ ", color = Color(0xFF1E293B)),
    KeyItem("سَجْدَة ۩", " ۩ ", color = Color(0xFF1E293B)),

    // Diacritics (Harakat)
    KeyItem("َ  (فَتْحَة)", "َ", color = Color(0xFF27272A)),
    KeyItem("ِ  (كَسْرَة)", "ِ", color = Color(0xFF27272A)),
    KeyItem("ُ  (ضَمَّة)", "ُ", color = Color(0xFF27272A)),
    KeyItem("ْ  (سُكُون)", "ْ", color = Color(0xFF27272A)),

    KeyItem("ً  (تَنْوِين)", "ً", color = Color(0xFF27272A)),
    KeyItem("ٍ  (تَنْوِين)", "ٍ", color = Color(0xFF27272A)),
    KeyItem("ٌ  (تَنْوِين)", "ٌ", color = Color(0xFF27272A)),
    KeyItem("ّ  (شَدَّة)", "ّ", color = Color(0xFF27272A)),

    // Arabic Alphabet
    KeyItem("ا"), KeyItem("ب"), KeyItem("ت"), KeyItem("ث"),
    KeyItem("ج"), KeyItem("ح"), KeyItem("خ"), KeyItem("د"),
    KeyItem("ذ"), KeyItem("ر"), KeyItem("ز"), KeyItem("س"),
    KeyItem("ش"), KeyItem("ص"), KeyItem("ض"), KeyItem("ط"),
    KeyItem("ظ"), KeyItem("ع"), KeyItem("غ"), KeyItem("ف"),
    KeyItem("ق"), KeyItem("ك"), KeyItem("ل"), KeyItem("م"),
    KeyItem("ن"), KeyItem("ه"), KeyItem("و"), KeyItem("ي")
)

private val onlineSkillsKeyItems = listOf(
    // Web Development & Code
    KeyItem("<html>", "```html\n<!DOCTYPE html>\n<html>\n  <head><title>Lesson</title></head>\n  <body>\n  </body>\n</html>\n```", color = Color(0xFF1E1B4B)),
    KeyItem("CSS", "/* Modern Responsive CSS */\n.card {\n  display: flex;\n  border-radius: 12px;\n}", color = Color(0xFF1E1B4B)),
    KeyItem("JavaScript", "const handleAction = async () => {\n  console.log('Online Skill Loaded');\n};", color = Color(0xFF1E1B4B)),
    KeyItem("Python", "# Python Automation\ndef solve_problem(data):\n    return [item.strip() for item in data]", color = Color(0xFF1E1B4B)),

    // Tech Tools & Data
    KeyItem("React", "import React, { useState } from 'react';", color = Color(0xFF0F766E)),
    KeyItem("API", "GET /api/v1/resource HTTP/1.1\nAuthorization: Bearer <TOKEN>", color = Color(0xFF0F766E)),
    KeyItem("SQL", "SELECT id, name, score FROM students WHERE score >= 80 ORDER BY score DESC;", color = Color(0xFF0F766E)),
    KeyItem("Git", "git checkout -b feature-update\ngit commit -m 'Add lesson'", color = Color(0xFF0F766E)),

    // Design & Digital Skills
    KeyItem("UI/UX", "[Design Principle: 8dp Grid, 60-30-10 Color Rule, High Contrast]", color = Color(0xFF4C1D95)),
    KeyItem("Figma", "[Figma: Auto-Layout Frame, Component Variant, Typography Styles]", color = Color(0xFF4C1D95)),
    KeyItem("Canva", "[Canva Design: Social Media Carousel • 1080x1350px • Clean Hierarchy]", color = Color(0xFF4C1D95)),
    KeyItem("Video Editing", "[CapCut / Premiere: 16:9 / 9:16 Cut • B-roll Layer • Audio Denoise]", color = Color(0xFF4C1D95)),

    // Business & Freelancing
    KeyItem("AI Prompts", "[Prompt: Act as an expert tutor. Break down this concept into 3 simple steps.]", color = Color(0xFF065F46)),
    KeyItem("Copywriting", "[Hook]: Stop struggling with code.\n[Value]: Here is the 1-minute fix.\n[CTA]: Try this snippet today.", color = Color(0xFF065F46)),
    KeyItem("Freelancing", "[Proposal Outline: Greeting • Client Problem • My Solution • Portfolio Proof • Pricing]", color = Color(0xFF065F46)),
    KeyItem("SEO & Ads", "[SEO Checklist: Title tag < 60 chars • Meta description • H1/H2 tags • Fast load]", color = Color(0xFF065F46)),

    // Project Blocks
    KeyItem("[Project Task]", "🎯 Practical Assignment:\n1. Open your code editor / tool\n2. Complete the exercise\n3. Share screenshot in class room", color = Color(0xFF1F2937)),
    KeyItem("[Key Takeaway]", "💡 Key Takeaway: Always test your work before shipping.", color = Color(0xFF1F2937)),
    KeyItem("[Tool Link]", "🔗 Recommended Free Tool: https://", color = Color(0xFF1F2937)),
    KeyItem("[Step-by-Step]", "📌 Step-by-Step Guide:\n• Step 1: \n• Step 2: \n• Step 3: ", color = Color(0xFF1F2937))
)

// -----------------------------------------------------------------------------
// ABC Keyboard: QWERTY Layout with 48px key heights
// -----------------------------------------------------------------------------
@Composable
private fun AbcKeyboardContent(
    isShift: Boolean,
    onShiftToggle: () -> Unit,
    onKeyClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSpace: () -> Unit
) {
    val scrollState = rememberScrollState()

    val row0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp), // bottom padding 20px
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Numbers row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row0.forEach { num ->
                KeyButton(label = num, modifier = Modifier.weight(1f).height(44.dp)) { onKeyClick(num) }
            }
        }

        // QWERTY Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row1.forEach { char ->
                val label = if (isShift) char.uppercase() else char
                KeyButton(label = label, modifier = Modifier.weight(1f).height(44.dp)) { onKeyClick(label) }
            }
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row2.forEach { char ->
                val label = if (isShift) char.uppercase() else char
                KeyButton(label = label, modifier = Modifier.weight(1f).height(44.dp)) { onKeyClick(label) }
            }
        }

        // Row 3 with Shift & Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift
            KeyButton(
                label = if (isShift) "⇧ ON" else "⇧",
                containerColor = if (isShift) Color(0xFF22C55E) else Color(0xFF3F3F46),
                modifier = Modifier.weight(1.3f).height(44.dp)
            ) { onShiftToggle() }

            row3.forEach { char ->
                val label = if (isShift) char.uppercase() else char
                KeyButton(label = label, modifier = Modifier.weight(1f).height(44.dp)) { onKeyClick(label) }
            }

            // Backspace
            KeyButton(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                },
                containerColor = Color(0xFF3F3F46),
                modifier = Modifier.weight(1.3f).height(44.dp)
            ) { onBackspace() }
        }

        // Row 4: Spacebar, punctuations, and Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(label = ",", modifier = Modifier.weight(0.9f).height(44.dp)) { onKeyClick(",") }
            KeyButton(label = ".", modifier = Modifier.weight(0.9f).height(44.dp)) { onKeyClick(".") }
            KeyButton(label = "?", modifier = Modifier.weight(0.9f).height(44.dp)) { onKeyClick("?") }

            // Spacebar
            KeyButton(
                label = "␣ Space",
                containerColor = Color(0xFF27272A),
                modifier = Modifier.weight(3.5f).height(44.dp)
            ) { onSpace() }

            // Enter
            KeyButton(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                },
                containerColor = Color(0xFF22C55E),
                modifier = Modifier.weight(1.4f).height(44.dp)
            ) { onEnter() }
        }
    }
}

// -----------------------------------------------------------------------------
// Shared Key Button Helper
// -----------------------------------------------------------------------------
@Composable
private fun KeyButton(
    label: String? = null,
    icon: (@Composable () -> Unit)? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    containerColor: Color = Color(0xFF27272A),
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F46)),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(
                    text = label,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else if (icon != null) {
                icon()
            }
        }
    }
}
