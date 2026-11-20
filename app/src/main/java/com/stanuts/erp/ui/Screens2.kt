package com.stanuts.erp.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stanuts.erp.*
import com.stanuts.erp.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ---------------- INVOICES ----------------
@Composable
fun InvoicesScreen(vm: AppVm) {
    val list by vm.invoices.collectAsState()
    var sel by remember { mutableStateOf<Invoice?>(null) }
    Screen(tr("Facturation", "Invoicing"), null) {
        items(list, key = { it.id }) { inv ->
            val rem = inv.total - inv.paid
            Card2({ sel = inv }) {
                Text(inv.number, fontWeight = FontWeight.Bold)
                Text("${inv.customerName}  •  ${fdate(inv.date)}")
                KV(tr("Total", "Total"), inv.total.fmt())
                KV(tr("Payé", "Paid"), inv.paid.fmt(), Green)
                KV(tr("Reste", "Remaining"), if (rem > 0.005) rem.fmt() else "0", if (rem > 0.005) Red else Green)
            }
        }
    }
    sel?.let { inv ->
        val lines by produceState<List<SaleLine>>(emptyList(), inv.id) { value = vm.dao.lines(inv.saleId) }
        AlertDialog(
            onDismissRequest = { sel = null },
            confirmButton = { TextButton(onClick = { sel = null }) { Text("OK") } },
            title = { Text(inv.number) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${inv.customerName} – ${fdate(inv.date)}")
                    lines.forEach {
                        Text("${it.qtyEntered} ${if (it.mode == "CARTON") "carton(s)" else tr("unité(s)", "unit(s)")} × ${it.productName}  =  ${(it.qtyUnits * it.unitPrice).fmt()}")
                    }
                    Text("Total: ${inv.total.fmt()}", fontWeight = FontWeight.Bold)
                    Text(tr("Payé: ", "Paid: ") + inv.paid.fmt())
                    Text(tr("Reste: ", "Remaining: ") + (inv.total - inv.paid).fmt())
                }
            }
        )
    }
}

// ---------------- EXPENSES ----------------
val categories = listOf("Loyer / Rent", "Transport", "Électricité-Eau / Utilities", "Carburant / Fuel",
    "Marketing", "Maintenance", "Emballage / Packaging", "Autre / Other")

@Composable
fun ExpensesScreen(vm: AppVm) {
    val list by vm.expenses.collectAsState()
    var add by remember { mutableStateOf(false) }
    Screen(tr("Gestion des Frais", "Expense Management"), { add = true }) {
        item {
            Card2 {
                KV(tr("Total des frais", "Total expenses"), list.sumOf { it.amount }.fmt(), Red)
                list.groupBy { it.category }.forEach { (c, l) -> KV(c, l.sumOf { it.amount }.fmt()) }
            }
        }
        items(list, key = { it.id }) { e ->
            Card2 {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(e.category, fontWeight = FontWeight.Bold)
                        Text("${fdate(e.date)}  ${e.note}")
                    }
                    Text(e.amount.fmt())
                    TextButton(onClick = { vm.deleteExpense(e) }) { Text("✕", color = Red) }
                }
            }
        }
    }
    if (add) {
        var ci by remember { mutableStateOf(0) }
        var note by remember { mutableStateOf("") }
        var amt by remember { mutableStateOf("") }
        Dlg(tr("Nouveau frais", "New expense"), { add = false }, {
            if (amt.d() > 0) { vm.addExpense(categories[ci], note, amt.d()); add = false }
        }) {
            Pick(tr("Catégorie", "Category"), categories, ci) { ci = it }
            Field(tr("Description", "Description"), note, { note = it })
            Field(tr("Montant", "Amount"), amt, { amt = it }, true)
        }
    }
}

// ---------------- PAYROLL ----------------
@Composable
fun PayrollScreen(vm: AppVm) {
    val list by vm.employees.collectAsState()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Employee?>(null) }
    var pay by remember { mutableStateOf<Employee?>(null) }
    Screen(tr("Gestion des Salaires", "Payroll"), { add = true }) {
        item {
            OutlinedButton(onClick = { vm.newPeriod() }) { Text(tr("Nouvelle période (remise à zéro du payé)", "New period (reset paid)")) }
        }
        items(list, key = { it.id }) { e ->
            val rem = e.baseSalary - e.paidAmount
            Card2({ pay = e }) {
                Text(e.name, fontWeight = FontWeight.Bold)
                KV(tr("Salaire de base", "Base salary"), e.baseSalary.fmt())
                KV(tr("Payé", "Paid"), e.paidAmount.fmt(), Green)
                KV(tr("Reste dû (toucher pour payer)", "Remaining (tap to pay)"), if (rem > 0.005) rem.fmt() else "0", if (rem > 0.005) Red else Green)
                TextButton(onClick = { edit = e }) { Text(tr("Modifier", "Edit")) }
            }
        }
    }
    if (add) EmployeeDialog(null, vm) { add = false }
    edit?.let { EmployeeDialog(it, vm) { edit = null } }
    pay?.let { e ->
        var amt by remember { mutableStateOf((e.baseSalary - e.paidAmount).coerceAtLeast(0.0).num()) }
        Dlg(tr("Payer ", "Pay ") + e.name, { pay = null }, { vm.paySalary(e.id, amt.d()); pay = null }, tr("Payer", "Pay")) {
            Field(tr("Montant", "Amount"), amt, { amt = it }, true)
        }
    }
}

@Composable
fun EmployeeDialog(e: Employee?, vm: AppVm, close: () -> Unit) {
    var name by remember { mutableStateOf(e?.name ?: "") }
    var base by remember { mutableStateOf((e?.baseSalary ?: 0.0).num()) }
    Dlg(tr("Employé", "Employee"), close, {
        if (name.isNotBlank()) { vm.saveEmployee(Employee(id = e?.id ?: 0, name = name.trim(), baseSalary = base.d(), paidAmount = e?.paidAmount ?: 0.0)); close() }
    }) {
        Field(tr("Nom", "Name"), name, { name = it })
        Field(tr("Salaire de base", "Base salary"), base, { base = it }, true)
        if (e != null) TextButton(onClick = { vm.deleteEmployee(e); close() }) { Text(tr("Supprimer", "Delete"), color = Red) }
    }
}

// ---------------- BACKUP ----------------
fun sendBackupEmail(ctx: Context, vm: AppVm, scope: CoroutineScope) {
    scope.launch {
        try {
            val f = vm.writeBackupFile()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                if (vm.email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(vm.email))
                putExtra(Intent.EXTRA_SUBJECT, "Stanuts ERP backup " + fdate(System.currentTimeMillis()))
                putExtra(Intent.EXTRA_TEXT, tr("Sauvegarde ci-jointe.", "Backup attached."))
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Email"))
            vm.markBackup()
        } catch (e: Exception) { vm.message = e.message }
    }
}

@Composable
fun BackupScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) vm.exportTo(u) }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) pending = u }
    Screen(tr("Sauvegarde & Transfert", "Backup & Transfer"), null) {
        item {
            Card2 {
                Text(tr("Export / Import (.json)", "Export / Import (.json)"), fontWeight = FontWeight.Bold)
                Text(tr("Exportez toute la base, puis importez-la sur un autre appareil.", "Export the whole database, then import it on another device."))
                Button(onClick = { exportL.launch("stanuts-backup-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + ".json") }) {
                    Text(tr("Exporter vers un fichier", "Export to file"))
                }
                OutlinedButton(onClick = { importL.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text(tr("Importer / restaurer", "Import / restore"))
                }
                Text(tr("⚠ L'import remplace toutes les données actuelles.", "⚠ Import replaces all current data."), color = Red)
            }
        }
        item {
            Card2 {
                Text(tr("Sauvegarde périodique par email", "Periodic email backup"), fontWeight = FontWeight.Bold)
                Field(tr("Email destinataire", "Recipient email"), vm.email, { vm.setEmail(it) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("Rappel hebdomadaire", "Weekly reminder"))
                    Switch(checked = vm.reminder, onCheckedChange = { vm.setReminder(it, ctx) })
                }
                Text(tr("Dernière sauvegarde : ", "Last backup: ") + if (vm.lastBackup == 0L) "—" else fdate(vm.lastBackup))
                Button(onClick = { sendBackupEmail(ctx, vm, scope) }) { Text(tr("Envoyer la sauvegarde par email", "Email backup now")) }
            }
        }
    }
    pending?.let { u ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("Restaurer ?", "Restore?")) },
            text = { Text(tr("Toutes les données actuelles seront remplacées.", "All current data will be replaced.")) },
            confirmButton = { Button(onClick = { vm.importFrom(u); pending = null }) { Text(tr("Restaurer", "Restore")) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("Annuler", "Cancel")) } }
        )
    }
}
