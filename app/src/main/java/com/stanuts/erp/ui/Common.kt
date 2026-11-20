package com.stanuts.erp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stanuts.erp.tr

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)

@Composable
fun Field(label: String, v: String, on: (String) -> Unit, num: Boolean = false) =
    OutlinedTextField(
        value = v, onValueChange = on, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )

@Composable
fun Pick(label: String, options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label : " + options.getOrElse(sel) { "-" })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSel(i); open = false }) }
        }
    }
}

@Composable
fun Dlg(
    title: String, onDismiss: () -> Unit, onOk: () -> Unit,
    okText: String = tr("Enregistrer", "Save"), body: @Composable ColumnScope.() -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { Button(onClick = onOk) { Text(okText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler", "Cancel")) } },
    title = { Text(title) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = body) }
)

@Composable
fun Screen(title: String, onAdd: (() -> Unit)?, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onAdd != null) Button(onClick = onAdd) { Text("+ " + tr("Ajouter", "Add")) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Card2(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun KV(a: String, b: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(a, modifier = Modifier.weight(1f))
        Text(b, color = color, fontWeight = FontWeight.Medium)
    }
}

fun modes() = listOf(tr("Unité (sachet)", "Unit (bag)"), tr("Carton (présentoir)", "Carton (display)"))
