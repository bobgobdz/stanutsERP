package com.stanuts.erp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.gson.Gson
import com.stanuts.erp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Calendar

data class LineIn(val product: Product, val mode: String, val qty: Int, val price: Double) // price per entered mode
fun LineIn.units() = if (mode == "CARTON") qty * maxOf(product.unitsPerCarton, 1) else qty
fun LineIn.total() = price * qty

class AppVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    val dao = db.dao()
    private val prefs = Prefs.get(app)
    private fun <T> Flow<T>.st(i: T) = stateIn(viewModelScope, SharingStarted.Eagerly, i)

    val products = dao.products().st(emptyList())
    val suppliers = dao.suppliers().st(emptyList())
    val customers = dao.customers().st(emptyList())
    val purchases = dao.purchases().st(emptyList())
    val sales = dao.sales().st(emptyList())
    val payments = dao.payments().st(emptyList())
    val invoices = dao.invoices().st(emptyList())
    val expenses = dao.expenses().st(emptyList())
    val employees = dao.employees().st(emptyList())
    val salaryPayments = dao.salaryPayments().st(emptyList())

    var message by mutableStateOf<String?>(null)
    var backupDue by mutableStateOf(false)
    var email by mutableStateOf(prefs.getString("email", "") ?: "")
    var reminder by mutableStateOf(prefs.getBoolean("reminder", false))
    var lastBackup by mutableStateOf(prefs.getLong("last", 0L))

    init {
        Lang.fr = prefs.getString("lang", "fr") == "fr"
        viewModelScope.launch { seed() }
        refreshDue()
        if (reminder) BackupScheduler.apply(app, true)
    }

    private suspend fun seed() {
        if (dao.productCount() > 0) return
        // Stanuts catalogue from the distributor memo (prices per unit)
        listOf(
            Product(name = "Stanuts Salée 12 g", sku = "STN-SAL-12", unitsPerCarton = 50, cartonsPerPallet = 280, purchasePrice = 11.0, priceGros = 12.5, priceDD = 13.5, salePrice = 15.0),
            Product(name = "Stanuts Salée 75 g", sku = "STN-SAL-75", unitsPerCarton = 12, cartonsPerPallet = 240, purchasePrice = 65.0, priceGros = 73.0, priceDD = 77.0, salePrice = 90.0),
            Product(name = "Stanuts Fumée 12 g", sku = "STN-FUM-12", unitsPerCarton = 50, cartonsPerPallet = 280, purchasePrice = 15.0, priceGros = 16.5, priceDD = 18.0, salePrice = 20.0),
            Product(name = "Stanuts Fumée 75 g", sku = "STN-FUM-75", unitsPerCarton = 12, cartonsPerPallet = 240, purchasePrice = 77.0, priceGros = 88.0, priceDD = 93.0, salePrice = 110.0),
            Product(name = "Stanuts Caramélisée 75 g", sku = "STN-CAR-75", unitsPerCarton = 12, cartonsPerPallet = 240, purchasePrice = 67.0, priceGros = 82.0, priceDD = 90.0, salePrice = 110.0)
        ).forEach { dao.saveProduct(it) }
    }

    fun toggleLang() { Lang.fr = !Lang.fr; prefs.edit().putString("lang", if (Lang.fr) "fr" else "en").apply() }

    // ---------- Inventory ----------
    fun saveProduct(p: Product) { viewModelScope.launch { dao.saveProduct(p) } }
    fun deleteProduct(p: Product) { viewModelScope.launch { dao.deleteProduct(p) } }
    fun saveSupplier(s: Supplier) { viewModelScope.launch { dao.saveSupplier(s) } }
    fun deleteSupplier(s: Supplier) { viewModelScope.launch { dao.deleteSupplier(s) } }
    fun saveCustomer(c: Customer) { viewModelScope.launch { dao.saveCustomer(c) } }
    fun deleteCustomer(c: Customer) { viewModelScope.launch { dao.deleteCustomer(c) } }

    // ---------- Purchases (stock goes up) ----------
    fun purchase(p: Product, mode: String, qty: Int, costPerMode: Double, supplier: String, lot: String, expiry: String, paid: Double?) {
        viewModelScope.launch {
            val upc = maxOf(p.unitsPerCarton, 1)
            val units = if (mode == "CARTON") qty * upc else qty
            if (units <= 0) return@launch
            val unitCost = if (mode == "CARTON") costPerMode / upc else costPerMode
            val total = unitCost * units
            val pd = (paid ?: total).coerceIn(0.0, total)
            db.withTransaction {
                dao.savePurchase(Purchase(productId = p.id, productName = p.name, supplier = supplier, qtyUnits = units,
                    unitCost = unitCost, total = total, amountPaid = pd, date = System.currentTimeMillis(), lot = lot, expiry = expiry))
                dao.addStock(p.id, units)
                dao.setCost(p.id, unitCost)
            }
            message = tr("Achat enregistré, stock mis à jour", "Purchase saved, stock updated")
        }
    }
    fun payPurchase(id: Long, amount: Double) {
        viewModelScope.launch {
            val p = dao.purchase(id) ?: return@launch
            val a = amount.coerceAtMost(p.total - p.amountPaid)
            if (a > 0) dao.payPurchase(id, a)
        }
    }

    // ---------- Sales (stock goes down) + invoice ----------
    fun sell(customer: Customer?, lines: List<LineIn>, paidNow: Double?) {
        viewModelScope.launch {
            if (lines.isEmpty()) return@launch
            val need = lines.groupBy { it.product.id }.mapValues { e -> e.value.sumOf { it.units() } }
            for ((id, n) in need) {
                val cur = dao.product(id)
                if (cur == null || cur.stock < n) {
                    message = tr("Stock insuffisant : ", "Insufficient stock: ") + (cur?.name ?: ""); return@launch
                }
            }
            val total = lines.sumOf { it.total() }
            val paid = (paidNow ?: total).coerceIn(0.0, total)
            val now = System.currentTimeMillis()
            val cname = customer?.name ?: tr("Client comptoir", "Walk-in customer")
            db.withTransaction {
                val sid = dao.saveSale(Sale(customerId = customer?.id ?: 0, customerName = cname, date = now, total = total, paid = paid))
                lines.forEach { l ->
                    val upc = if (l.mode == "CARTON") maxOf(l.product.unitsPerCarton, 1) else 1
                    dao.saveLine(SaleLine(saleId = sid, productId = l.product.id, productName = l.product.name,
                        qtyUnits = l.units(), unitPrice = l.price / upc, mode = l.mode, qtyEntered = l.qty))
                    dao.addStock(l.product.id, -l.units())
                }
                if (paid > 0) dao.savePayment(Payment(saleId = sid, amount = paid, date = now))
                val n = dao.invoiceCount() + 1
                val year = Calendar.getInstance().get(Calendar.YEAR)
                dao.saveInvoice(Invoice(number = "FAC-%d-%04d".format(year, n), saleId = sid, customerName = cname, date = now, total = total, paid = paid))
            }
            message = tr("Vente et facture créées", "Sale and invoice created")
        }
    }

    fun settle(saleId: Long, amount: Double) {
        viewModelScope.launch {
            val s = dao.sale(saleId) ?: return@launch
            val a = amount.coerceAtMost(s.total - s.paid)
            if (a <= 0) return@launch
            db.withTransaction {
                dao.savePayment(Payment(saleId = saleId, amount = a, date = System.currentTimeMillis()))
                dao.paySale(saleId, a)
                dao.payInvoice(saleId, a)
            }
            message = tr("Règlement enregistré", "Payment recorded")
        }
    }

    // ---------- Expenses & payroll ----------
    fun addExpense(cat: String, note: String, amount: Double) {
        viewModelScope.launch { dao.saveExpense(Expense(category = cat, note = note, amount = amount, date = System.currentTimeMillis())) }
    }
    fun deleteExpense(e: Expense) { viewModelScope.launch { dao.deleteExpense(e) } }
    fun saveEmployee(e: Employee) { viewModelScope.launch { dao.saveEmployee(e) } }
    fun deleteEmployee(e: Employee) { viewModelScope.launch { dao.deleteEmployee(e) } }
    fun paySalary(id: Long, amount: Double) {
        viewModelScope.launch {
            val e = dao.employee(id) ?: return@launch
            val a = amount.coerceAtMost(e.baseSalary - e.paidAmount)
            if (a <= 0) return@launch
            db.withTransaction {
                dao.saveSalaryPayment(SalaryPayment(employeeId = id, employeeName = e.name, amount = a, date = System.currentTimeMillis()))
                dao.payEmployee(id, a)
            }
        }
    }
    fun newPeriod() { viewModelScope.launch { dao.resetPeriod() } }

    // ---------- Backup ----------
    private suspend fun buildJson(): String = Gson().toJson(Backup(
        products = dao.productsOnce(), suppliers = dao.suppliersOnce(), customers = dao.customersOnce(),
        purchases = dao.purchasesOnce(), sales = dao.salesOnce(), saleLines = dao.saleLinesOnce(),
        payments = dao.paymentsOnce(), invoices = dao.invoicesOnce(), expenses = dao.expensesOnce(),
        employees = dao.employeesOnce(), salaryPayments = dao.salaryPaymentsOnce()))

    fun setEmail(v: String) { email = v; prefs.edit().putString("email", v).apply() }
    fun setReminder(on: Boolean, ctx: Context) {
        reminder = on; prefs.edit().putBoolean("reminder", on).apply()
        BackupScheduler.apply(ctx.applicationContext, on); refreshDue()
    }
    fun refreshDue() {
        backupDue = reminder && (prefs.getBoolean("due", false) || System.currentTimeMillis() - lastBackup > 7 * 86400000L)
    }
    fun markBackup() {
        lastBackup = System.currentTimeMillis()
        prefs.edit().putLong("last", lastBackup).putBoolean("due", false).apply(); refreshDue()
    }

    suspend fun writeBackupFile(): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "backups").apply { mkdirs() }
        val f = File(dir, "stanuts-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json")
        f.writeText(buildJson()); f
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = buildJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }
                markBackup(); message = tr("Export réussi", "Export successful")
            } catch (e: Exception) { message = tr("Erreur d'export : ", "Export error: ") + e.message }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val txt = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                val b = Gson().fromJson(txt, Backup::class.java) ?: throw IllegalArgumentException("Invalid file")
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                    db.withTransaction {
                        dao.putProducts(b.products); dao.putSuppliers(b.suppliers); dao.putCustomers(b.customers)
                        dao.putPurchases(b.purchases); dao.putSales(b.sales); dao.putSaleLines(b.saleLines)
                        dao.putPayments(b.payments); dao.putInvoices(b.invoices); dao.putExpenses(b.expenses)
                        dao.putEmployees(b.employees); dao.putSalaryPayments(b.salaryPayments)
                    }
                }
                message = tr("Import réussi", "Import successful")
            } catch (e: Exception) { message = tr("Erreur d'import : ", "Import error: ") + e.message }
        }
    }
}
