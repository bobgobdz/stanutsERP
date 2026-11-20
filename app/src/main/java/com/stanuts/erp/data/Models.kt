package com.stanuts.erp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// All stock quantities are stored in UNITS (sachets). Cartons (présentoirs) = unitsPerCarton units.
@Entity data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val sku: String = "", val stock: Int = 0,
    val unitsPerCarton: Int = 1, val cartonsPerPallet: Int = 0,
    val purchasePrice: Double = 0.0,   // per unit (prix distrib.)
    val priceGros: Double = 0.0, val priceDD: Double = 0.0, val salePrice: Double = 0.0 // per unit
)
@Entity data class Supplier(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String = "")
@Entity data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String = "", val tier: String = "PUBLIC")
@Entity data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val productId: Long, val productName: String,
    val supplier: String, val qtyUnits: Int, val unitCost: Double, val total: Double, val amountPaid: Double,
    val date: Long, val lot: String = "", val expiry: String = ""
)
@Entity data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val customerName: String,
    val date: Long, val total: Double, val paid: Double
)
@Entity data class SaleLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val productId: Long, val productName: String,
    val qtyUnits: Int, val unitPrice: Double, val mode: String, val qtyEntered: Int
)
@Entity data class Payment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val amount: Double, val date: Long)
@Entity data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val number: String, val saleId: Long,
    val customerName: String, val date: Long, val total: Double, val paid: Double
)
@Entity data class Expense(@PrimaryKey(autoGenerate = true) val id: Long = 0, val category: String, val note: String, val amount: Double, val date: Long)
@Entity data class Employee(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val baseSalary: Double, val paidAmount: Double = 0.0)
@Entity data class SalaryPayment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val employeeId: Long, val employeeName: String, val amount: Double, val date: Long)

data class Backup(
    val version: Int = 1,
    val products: List<Product> = emptyList(), val suppliers: List<Supplier> = emptyList(),
    val customers: List<Customer> = emptyList(), val purchases: List<Purchase> = emptyList(),
    val sales: List<Sale> = emptyList(), val saleLines: List<SaleLine> = emptyList(),
    val payments: List<Payment> = emptyList(), val invoices: List<Invoice> = emptyList(),
    val expenses: List<Expense> = emptyList(), val employees: List<Employee> = emptyList(),
    val salaryPayments: List<SalaryPayment> = emptyList()
)

@Dao interface AppDao {
    @Query("SELECT * FROM Product ORDER BY name") fun products(): Flow<List<Product>>
    @Query("SELECT COUNT(*) FROM Product") suspend fun productCount(): Int
    @Query("SELECT * FROM Product WHERE id=:id") suspend fun product(id: Long): Product?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProduct(p: Product): Long
    @Delete suspend fun deleteProduct(p: Product)
    @Query("UPDATE Product SET stock=stock+:d WHERE id=:id") suspend fun addStock(id: Long, d: Int)
    @Query("UPDATE Product SET purchasePrice=:c WHERE id=:id") suspend fun setCost(id: Long, c: Double)

    @Query("SELECT * FROM Supplier ORDER BY name") fun suppliers(): Flow<List<Supplier>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSupplier(s: Supplier): Long
    @Delete suspend fun deleteSupplier(s: Supplier)

    @Query("SELECT * FROM Customer ORDER BY name") fun customers(): Flow<List<Customer>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCustomer(c: Customer): Long
    @Delete suspend fun deleteCustomer(c: Customer)

    @Query("SELECT * FROM Purchase ORDER BY date DESC") fun purchases(): Flow<List<Purchase>>
    @Insert suspend fun savePurchase(p: Purchase): Long
    @Query("SELECT * FROM Purchase WHERE id=:id") suspend fun purchase(id: Long): Purchase?
    @Query("UPDATE Purchase SET amountPaid=amountPaid+:a WHERE id=:id") suspend fun payPurchase(id: Long, a: Double)

    @Query("SELECT * FROM Sale ORDER BY date DESC") fun sales(): Flow<List<Sale>>
    @Query("SELECT * FROM Sale WHERE id=:id") suspend fun sale(id: Long): Sale?
    @Insert suspend fun saveSale(s: Sale): Long
    @Insert suspend fun saveLine(l: SaleLine): Long
    @Query("SELECT * FROM SaleLine WHERE saleId=:id") suspend fun lines(id: Long): List<SaleLine>
    @Query("UPDATE Sale SET paid=paid+:a WHERE id=:id") suspend fun paySale(id: Long, a: Double)

    @Query("SELECT * FROM Payment") fun payments(): Flow<List<Payment>>
    @Insert suspend fun savePayment(p: Payment): Long

    @Query("SELECT * FROM Invoice ORDER BY id DESC") fun invoices(): Flow<List<Invoice>>
    @Query("SELECT COUNT(*) FROM Invoice") suspend fun invoiceCount(): Int
    @Insert suspend fun saveInvoice(i: Invoice): Long
    @Query("UPDATE Invoice SET paid=paid+:a WHERE saleId=:id") suspend fun payInvoice(id: Long, a: Double)

    @Query("SELECT * FROM Expense ORDER BY date DESC") fun expenses(): Flow<List<Expense>>
    @Insert suspend fun saveExpense(e: Expense): Long
    @Delete suspend fun deleteExpense(e: Expense)

    @Query("SELECT * FROM Employee ORDER BY name") fun employees(): Flow<List<Employee>>
    @Query("SELECT * FROM Employee WHERE id=:id") suspend fun employee(id: Long): Employee?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEmployee(e: Employee): Long
    @Delete suspend fun deleteEmployee(e: Employee)
    @Query("UPDATE Employee SET paidAmount=paidAmount+:a WHERE id=:id") suspend fun payEmployee(id: Long, a: Double)
    @Query("UPDATE Employee SET paidAmount=0") suspend fun resetPeriod()

    @Query("SELECT * FROM SalaryPayment") fun salaryPayments(): Flow<List<SalaryPayment>>
    @Insert suspend fun saveSalaryPayment(s: SalaryPayment): Long

    // ---- backup helpers ----
    @Query("SELECT * FROM Product") suspend fun productsOnce(): List<Product>
    @Query("SELECT * FROM Supplier") suspend fun suppliersOnce(): List<Supplier>
    @Query("SELECT * FROM Customer") suspend fun customersOnce(): List<Customer>
    @Query("SELECT * FROM Purchase") suspend fun purchasesOnce(): List<Purchase>
    @Query("SELECT * FROM Sale") suspend fun salesOnce(): List<Sale>
    @Query("SELECT * FROM SaleLine") suspend fun saleLinesOnce(): List<SaleLine>
    @Query("SELECT * FROM Payment") suspend fun paymentsOnce(): List<Payment>
    @Query("SELECT * FROM Invoice") suspend fun invoicesOnce(): List<Invoice>
    @Query("SELECT * FROM Expense") suspend fun expensesOnce(): List<Expense>
    @Query("SELECT * FROM Employee") suspend fun employeesOnce(): List<Employee>
    @Query("SELECT * FROM SalaryPayment") suspend fun salaryPaymentsOnce(): List<SalaryPayment>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProducts(l: List<Product>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSuppliers(l: List<Supplier>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCustomers(l: List<Customer>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPurchases(l: List<Purchase>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSales(l: List<Sale>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSaleLines(l: List<SaleLine>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPayments(l: List<Payment>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInvoices(l: List<Invoice>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putExpenses(l: List<Expense>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEmployees(l: List<Employee>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSalaryPayments(l: List<SalaryPayment>)
}

@Database(
    entities = [Product::class, Supplier::class, Customer::class, Purchase::class, Sale::class, SaleLine::class,
        Payment::class, Invoice::class, Expense::class, Employee::class, SalaryPayment::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "erp.db").build().also { inst = it }
        }
    }
}
