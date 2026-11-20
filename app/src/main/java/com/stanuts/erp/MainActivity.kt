package com.stanuts.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanuts.erp.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3F2B96), secondary = Color(0xFFD99A4E))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App(vm: AppVm = viewModel()) {
    val tabs = listOf(
        tr("Tableau de bord", "Dashboard"), tr("Stock", "Inventory"), tr("Achats", "Purchases"),
        tr("Ventes & Crédit", "Sales & Credit"), tr("Clients", "Customers"), tr("Factures", "Invoices"),
        tr("Frais", "Expenses"), tr("Salaires", "Payroll"), tr("Sauvegarde", "Backup")
    )
    var tab by rememberSaveable { mutableStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stanuts ERP", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.toggleLang() }) { Text(if (Lang.fr) "FR → EN" else "EN → FR") }
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> StockScreen(vm)
                2 -> PurchasesScreen(vm)
                3 -> SalesScreen(vm)
                4 -> CustomersScreen(vm)
                5 -> InvoicesScreen(vm)
                6 -> ExpensesScreen(vm)
                7 -> PayrollScreen(vm)
                else -> BackupScreen(vm)
            }
        }
    }
}
