package com.stanuts.erp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanuts.erp.*
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

@Composable
fun DashboardScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by vm.products.collectAsState()
    val sales by vm.sales.collectAsState()
    val payments by vm.payments.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val employees by vm.employees.collectAsState()
    val salPays by vm.salaryPayments.collectAsState()
    LaunchedEffect(Unit) { vm.refreshDue() }

    val cashIn = payments.sumOf { it.amount }
    val purchPaid = purchases.sumOf { it.amountPaid }
    val expTotal = expenses.sumOf { it.amount }
    val salPaid = salPays.sumOf { it.amount }
    val cash = cashIn - purchPaid - expTotal - salPaid
    val credit = sales.sumOf { it.total - it.paid }
    val stockValue = products.sumOf { it.stock * it.purchasePrice }
    val salDue = employees.sumOf { max(0.0, it.baseSalary - it.paidAmount) }
    val salPaidPeriod = employees.sumOf { it.paidAmount }
    val payable = purchases.sumOf { it.total - it.amountPaid }

    // weekly net performance (8 weeks, Monday start)
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val starts = (7 downTo 0).map { k -> (cal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -k) }.timeInMillis }
    val endAll = (cal.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
    val nets = starts.indices.map { i ->
        val s = starts[i]; val e = if (i < 7) starts[i + 1] else endAll
        fun inW(t: Long) = t >= s && t < e
        payments.filter { inW(it.date) }.sumOf { it.amount } - purchases.filter { inW(it.date) }.sumOf { it.amountPaid } -
            expenses.filter { inW(it.date) }.sumOf { it.amount } - salPays.filter { inW(it.date) }.sumOf { it.amount }
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(tr("Tableau de Bord", "Dashboard"), style = MaterialTheme.typography.titleLarge) }
        if (vm.backupDue) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(tr("⚠ Sauvegarde hebdomadaire à effectuer", "⚠ Weekly backup due"), fontWeight = FontWeight.Bold)
                    Button(onClick = { sendBackupEmail(ctx, vm, scope) }) { Text(tr("Envoyer la sauvegarde par email", "Email backup now")) }
                }
            }
        }
        item {
            Card2 {
                Text(tr("Trésorerie & Créances", "Cash & Receivables"), fontWeight = FontWeight.Bold)
                KV(tr("Cash en caisse", "Cash on hand"), cash.fmt(), if (cash >= 0) Green else Red)
                KV(tr("Crédit clients dû", "Customer credit owed"), credit.fmt(), Red)
                KV(tr("Dettes fournisseurs", "Supplier payables"), payable.fmt())
                KV(tr("Valeur du stock", "Stock value"), stockValue.fmt())
            }
        }
        item {
            Card2 {
                Text(tr("Frais & Salaires", "Expenses & Payroll"), fontWeight = FontWeight.Bold)
                KV(tr("Frais payés", "Expenses paid"), expTotal.fmt())
                KV(tr("Salaires payés (période)", "Salaries paid (period)"), salPaidPeriod.fmt())
                KV(tr("Salaires restant dus", "Salaries still due"), salDue.fmt(), if (salDue > 0) Red else Color.Unspecified)
            }
        }
        item {
            Card2 {
                Text(tr("Performance nette hebdomadaire", "Weekly net performance"), fontWeight = FontWeight.Bold)
                Text(tr("Encaissements − achats payés − frais − salaires", "Collections − purchases paid − expenses − salaries"),
                    style = MaterialTheme.typography.bodySmall)
                val mx = nets.maxOf { abs(it) }.coerceAtLeast(1.0)
                Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                    val mid = size.height / 2; val bw = size.width / nets.size
                    nets.forEachIndexed { i, v ->
                        val h = (abs(v) / mx * (mid - 6.dp.toPx())).toFloat()
                        drawRect(if (v >= 0) Green else Red, Offset(i * bw + bw * 0.15f, if (v >= 0) mid - h else mid), Size(bw * 0.7f, h))
                    }
                    drawLine(Color.Gray, Offset(0f, mid), Offset(size.width, mid), 2f)
                }
                nets.indices.reversed().forEach { i ->
                    val diff = if (i > 0) nets[i] - nets[i - 1] else null
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Sem. ", "Wk ") + fdate(starts[i]).take(5))
                        Text(nets[i].fmt(), color = if (nets[i] >= 0) Green else Red)
                        Text(if (diff == null) "—" else (if (diff >= 0) "+" else "−") + abs(diff).fmt(), color = if (diff == null) Color.Gray else if (diff >= 0) Green else Red)
                    }
                }
            }
        }
    }
}
