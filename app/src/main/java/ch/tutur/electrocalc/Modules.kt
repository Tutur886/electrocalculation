package ch.tutur.electrocalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

data class Module(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val content: @Composable () -> Unit
)

// ---------- Helpers ----------

fun f(v: Double, digits: Int = 3): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    return String.format(Locale.US, "%.${digits}f", v)
}

fun ohm(v: Double): String = when {
    v.isNaN() || v.isInfinite() -> "—"
    abs(v) >= 1e6 -> f(v / 1e6, 3) + " MΩ"
    abs(v) >= 1e3 -> f(v / 1e3, 3) + " kΩ"
    else -> f(v, 2) + " Ω"
}

fun hertz(v: Double): String = when {
    v.isNaN() || v.isInfinite() -> "—"
    abs(v) >= 1e6 -> f(v / 1e6, 3) + " MHz"
    abs(v) >= 1e3 -> f(v / 1e3, 3) + " kHz"
    else -> f(v, 2) + " Hz"
}

private val E12 = listOf(10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82)

fun nextE12(r: Double): Double {
    if (!(r > 0.0)) return Double.NaN
    var best = Double.NaN
    for (k in -1..6) for (b in E12) {
        val v = b.toDouble() * 10.0.pow(k)
        if (v >= r && (best.isNaN() || v < best)) best = v
    }
    return best
}

@Composable
fun Field(label: String, state: MutableState<String>, unit: String = "") {
    OutlinedTextField(
        value = state.value,
        onValueChange = { v -> state.value = v.filter { it.isDigit() || it == '.' || it == '-' } },
        label = { Text(if (unit.isEmpty()) label else "$label ($unit)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TextFieldFree(label: String, state: MutableState<String>) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun Picker(label: String, options: List<String>, index: MutableState<Int>) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options[index.value])
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { index.value = i; open = false })
            }
        }
    }
}

@Composable
fun ResultBox(lines: List<String>) {
    if (lines.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            lines.forEach { Text(it, fontFamily = FontFamily.Monospace) }
        }
    }
}

fun parseList(s: String): List<Double> =
    s.split(",", ";", " ", "\n")
        .mapNotNull { it.trim().toDoubleOrNull() }
        .filter { it > 0.0 }

// ---------- Modules ----------

@Composable
private fun ModOhm() {
    val u = remember { mutableStateOf("") }
    val i = remember { mutableStateOf("") }
    val r = remember { mutableStateOf("") }
    Field("Tension U", u, "V")
    Field("Courant I", i, "A")
    Field("Résistance R", r, "Ω")
    var U = u.value.toDoubleOrNull()
    var I = i.value.toDoubleOrNull()
    var R = r.value.toDoubleOrNull()
    if (U != null && I != null) R = U / I
    else if (U != null && R != null) I = U / R
    else if (I != null && R != null) U = I * R
    val out = if (U != null && I != null && R != null) {
        listOf("U = ${f(U)} V", "I = ${f(I)} A", "R = ${ohm(R)}", "P = ${f(U * I)} W")
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModLed() {
    val vs = remember { mutableStateOf("") }
    val vled = remember { mutableStateOf("") }
    val iled = remember { mutableStateOf("") }
    Field("Tension source Vs", vs, "V")
    Field("Tension LED Vf", vled, "V")
    Field("Courant LED", iled, "mA")
    val Vs = vs.value.toDoubleOrNull()
    val Vf = vled.value.toDoubleOrNull()
    val Im = iled.value.toDoubleOrNull()
    val out = if (Vs != null && Vf != null && Im != null && Im > 0) {
        val amps = Im / 1000.0
        val r = (Vs - Vf) / amps
        val p = (Vs - Vf) * amps
        if (r < 0) listOf("Vs doit être > Vf")
        else listOf(
            "R = ${ohm(r)}",
            "R normalisée (E12) = ${ohm(nextE12(r))}",
            "Puissance résistance = ${f(p, 3)} W"
        )
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun Mod555() {
    val r1 = remember { mutableStateOf("") }
    val r2 = remember { mutableStateOf("") }
    val c = remember { mutableStateOf("") }
    Field("R1", r1, "Ω")
    Field("R2", r2, "Ω")
    Field("C", c, "µF")
    val R1 = r1.value.toDoubleOrNull()
    val R2 = r2.value.toDoubleOrNull()
    val C = c.value.toDoubleOrNull()
    val out = if (R1 != null && R2 != null && C != null && C > 0) {
        val cf = C * 1e-6
        val freq = 1.44 / ((R1 + 2 * R2) * cf)
        val t = 1.0 / freq
        val duty = (R1 + R2) / (R1 + 2 * R2) * 100.0
        val tHigh = 0.693 * (R1 + R2) * cf
        val tLow = 0.693 * R2 * cf
        listOf(
            "Fréquence = ${hertz(freq)}",
            "Période = ${f(t * 1000, 3)} ms",
            "Rapport cyclique = ${f(duty, 1)} %",
            "T haut = ${f(tHigh * 1000, 3)} ms",
            "T bas = ${f(tLow * 1000, 3)} ms"
        )
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModDivider() {
    val vin = remember { mutableStateOf("") }
    val r1 = remember { mutableStateOf("") }
    val r2 = remember { mutableStateOf("") }
    Field("Tension entrée Vin", vin, "V")
    Field("R1 (haut)", r1, "Ω")
    Field("R2 (bas)", r2, "Ω")
    val Vin = vin.value.toDoubleOrNull()
    val R1 = r1.value.toDoubleOrNull()
    val R2 = r2.value.toDoubleOrNull()
    val out = if (Vin != null && R1 != null && R2 != null && (R1 + R2) > 0) {
        val vout = Vin * R2 / (R1 + R2)
        val i = Vin / (R1 + R2)
        listOf("Vout = ${f(vout)} V", "Courant = ${f(i * 1000, 3)} mA")
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModResCombo() {
    val s = remember { mutableStateOf("") }
    TextFieldFree("Résistances (Ω), séparées par des virgules", s)
    val list = parseList(s.value)
    val out = if (list.isNotEmpty()) {
        val serie = list.sum()
        val para = 1.0 / list.sumOf { 1.0 / it }
        listOf(
            "${list.size} résistance(s)",
            "Série = ${ohm(serie)}",
            "Parallèle = ${ohm(para)}"
        )
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModCapCombo() {
    val s = remember { mutableStateOf("") }
    TextFieldFree("Condensateurs (µF), séparés par des virgules", s)
    val list = parseList(s.value)
    val out = if (list.isNotEmpty()) {
        val para = list.sum()
        val serie = 1.0 / list.sumOf { 1.0 / it }
        listOf(
            "${list.size} condensateur(s)",
            "Parallèle = ${f(para, 3)} µF",
            "Série = ${f(serie, 4)} µF"
        )
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModRcFilter() {
    val r = remember { mutableStateOf("") }
    val c = remember { mutableStateOf("") }
    Field("R", r, "Ω")
    Field("C", c, "µF")
    val R = r.value.toDoubleOrNull()
    val C = c.value.toDoubleOrNull()
    val out = if (R != null && C != null && R > 0 && C > 0) {
        val fc = 1.0 / (2 * PI * R * C * 1e-6)
        listOf(
            "Fréquence de coupure = ${hertz(fc)}",
            "τ (constante) = ${f(R * C * 1e-6 * 1000, 3)} ms"
        )
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModReactance() {
    val freq = remember { mutableStateOf("") }
    val c = remember { mutableStateOf("") }
    val l = remember { mutableStateOf("") }
    Field("Fréquence f", freq, "Hz")
    Field("Condensateur C", c, "µF")
    Field("Inductance L", l, "mH")
    val fr = freq.value.toDoubleOrNull()
    val out = buildList {
        val C = c.value.toDoubleOrNull()
        val L = l.value.toDoubleOrNull()
        if (fr != null && C != null && C > 0) add("Xc = ${ohm(1.0 / (2 * PI * fr * C * 1e-6))}")
        if (fr != null && L != null && L > 0) add("XL = ${ohm(2 * PI * fr * L * 1e-3)}")
    }
    ResultBox(out)
}

@Composable
private fun ModLm317() {
    val r1 = remember { mutableStateOf("") }
    val r2 = remember { mutableStateOf("") }
    Field("R1 (ADJ→OUT)", r1, "Ω")
    Field("R2 (ADJ→GND)", r2, "Ω")
    val R1 = r1.value.toDoubleOrNull()
    val R2 = r2.value.toDoubleOrNull()
    val out = if (R1 != null && R2 != null && R1 > 0) {
        val vout = 1.25 * (1 + R2 / R1)
        listOf("Vout = ${f(vout)} V", "(Vref = 1,25 V)")
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModBaseConv() {
    val input = remember { mutableStateOf("") }
    val base = remember { mutableStateOf(0) }
    Picker("Base d'entrée", listOf("Décimal", "Binaire", "Hexadécimal"), base)
    TextFieldFree("Valeur", input)
    val radix = when (base.value) { 1 -> 2; 2 -> 16; else -> 10 }
    val n = input.value.trim().toLongOrNull(radix)
    val out = if (n != null) {
        listOf(
            "Décimal = $n",
            "Binaire = ${n.toString(2)}",
            "Hexadécimal = ${n.toString(16).uppercase()}",
            "Octal = ${n.toString(8)}"
        )
    } else if (input.value.isNotBlank()) listOf("Valeur invalide pour cette base")
    else emptyList()
    ResultBox(out)
}

@Composable
private fun ModLogic() {
    val a = remember { mutableStateOf(0) }
    val b = remember { mutableStateOf(0) }
    Picker("Entrée A", listOf("0", "1"), a)
    Picker("Entrée B", listOf("0", "1"), b)
    val A = a.value == 1
    val B = b.value == 1
    fun bit(x: Boolean) = if (x) "1" else "0"
    val out = listOf(
        "AND  = ${bit(A && B)}",
        "OR   = ${bit(A || B)}",
        "XOR  = ${bit(A != B)}",
        "NAND = ${bit(!(A && B))}",
        "NOR  = ${bit(!(A || B))}",
        "XNOR = ${bit(A == B)}"
    )
    ResultBox(out)
}

@Composable
private fun ModTriphase() {
    val u = remember { mutableStateOf("") }
    val i = remember { mutableStateOf("") }
    val cos = remember { mutableStateOf("") }
    Field("Tension de ligne U", u, "V")
    Field("Courant I", i, "A")
    Field("cos φ", cos, "")
    val U = u.value.toDoubleOrNull()
    val I = i.value.toDoubleOrNull()
    val out = buildList {
        if (U != null) add("Tension de phase = ${f(U / sqrt(3.0))} V")
        val c = cos.value.toDoubleOrNull()
        if (U != null && I != null) {
            val s = sqrt(3.0) * U * I
            add("Puissance apparente S = ${f(s / 1000, 3)} kVA")
            if (c != null) add("Puissance active P = ${f(s * c / 1000, 3)} kW")
        }
    }
    ResultBox(out)
}

@Composable
private fun ModVoltageDrop() {
    val i = remember { mutableStateOf("") }
    val l = remember { mutableStateOf("") }
    val sec = remember { mutableStateOf("") }
    val u = remember { mutableStateOf("") }
    Field("Courant I", i, "A")
    Field("Longueur (aller simple)", l, "m")
    Field("Section du câble", sec, "mm²")
    Field("Tension source (option, pour %)", u, "V")
    val I = i.value.toDoubleOrNull()
    val L = l.value.toDoubleOrNull()
    val S = sec.value.toDoubleOrNull()
    val out = if (I != null && L != null && S != null && S > 0) {
        val rho = 0.0178 // cuivre, Ω·mm²/m
        val du = 2 * rho * L * I / S
        val lines = mutableListOf("Chute de tension ΔU = ${f(du, 3)} V")
        val U = u.value.toDoubleOrNull()
        if (U != null && U > 0) lines.add("Soit ${f(du / U * 100, 2)} %")
        lines
    } else emptyList()
    ResultBox(out)
}

@Composable
private fun ModBattery() {
    val cap = remember { mutableStateOf("") }
    val draw = remember { mutableStateOf("") }
    Field("Capacité batterie", cap, "mAh")
    Field("Consommation", draw, "mA")
    val C = cap.value.toDoubleOrNull()
    val D = draw.value.toDoubleOrNull()
    val out = if (C != null && D != null && D > 0) {
        val hIdeal = C / D
        val hReal = hIdeal * 0.8
        fun hm(h: Double): String {
            val hh = h.toInt()
            val mm = ((h - hh) * 60).toInt()
            return "${hh} h ${mm} min"
        }
        listOf(
            "Autonomie idéale = ${f(hIdeal, 2)} h (${hm(hIdeal)})",
            "Autonomie réaliste (≈80 %) = ${hm(hReal)}"
        )
    } else emptyList()
    ResultBox(out)
}

// ---------- Registre ----------

val MODULES: List<Module> = listOf(
    Module("ohm", "Loi d'Ohm", listOf("ohm", "tension", "courant", "resistance", "résistance", "puissance", "volt", "ampere", "ampère", "watt", "u", "i", "r", "p")) { ModOhm() },
    Module("led", "Résistance pour LED", listOf("led", "diode", "resistance", "serie", "série", "limitation")) { ModLed() },
    Module("ne555", "Timer 555 (astable)", listOf("555", "ne555", "timer", "oscillateur", "frequence", "fréquence", "astable")) { Mod555() },
    Module("divider", "Diviseur de tension", listOf("diviseur", "pont", "tension", "vout", "diviseur de tension")) { ModDivider() },
    Module("rescombo", "Résistances série / parallèle", listOf("resistance", "résistance", "serie", "parallele", "parallèle", "combinaison")) { ModResCombo() },
    Module("capcombo", "Condensateurs série / parallèle", listOf("condensateur", "capacite", "capacité", "serie", "parallele", "parallèle", "farad")) { ModCapCombo() },
    Module("rcfilter", "Filtre RC", listOf("filtre", "rc", "coupure", "passe-bas", "passe-haut", "frequence", "fréquence")) { ModRcFilter() },
    Module("reactance", "Réactance (Xc / XL)", listOf("reactance", "réactance", "impedance", "impédance", "condensateur", "inductance", "bobine", "xc", "xl")) { ModReactance() },
    Module("lm317", "Régulateur LM317", listOf("lm317", "regulateur", "régulateur", "tension", "alimentation")) { ModLm317() },
    Module("baseconv", "Convertisseur bin / déc / hex", listOf("binaire", "decimal", "décimal", "hexadecimal", "hexadécimal", "conversion", "base", "bin", "hex", "oct")) { ModBaseConv() },
    Module("logic", "Portes logiques", listOf("logique", "porte", "and", "or", "xor", "nand", "nor", "boolean", "booleen", "booléen")) { ModLogic() },
    Module("triphase", "Triphasé", listOf("triphase", "triphasé", "phase", "ligne", "etoile", "étoile", "puissance", "400v")) { ModTriphase() },
    Module("vdrop", "Chute de tension (câble)", listOf("chute", "cable", "câble", "section", "longueur", "cuivre", "fil")) { ModVoltageDrop() },
    Module("battery", "Autonomie batterie", listOf("batterie", "autonomie", "accu", "mah", "duree", "durée", "consommation")) { ModBattery() }
)

@Composable
fun ModuleBody(id: String) {
    val m = MODULES.firstOrNull { it.id == id }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (m == null) {
            Text("Module introuvable.")
        } else {
            m.content()
        }
        Spacer(Modifier.height(4.dp))
    }
}
