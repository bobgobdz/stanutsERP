package com.stanuts.erp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanuts.erp.*
import com.stanuts.erp.data.*

fun cartonsText(p: Product): String {
    val u = maxOf(p.unitsPerCarton, 1)
    return "${p.stock / u} cartons + ${p.stock % u} " + tr("unités", "units")
}
fun Product.priceFor(tier: String) = when (tier) { "GROS" -> priceGros; "DD" -> priceDD; else -> salePrice }
fun status(s: Sale) = when {
    s.paid >= s.total - 0.005 -> tr("Payé", "Paid")
    s.paid <= 0.005 -> tr("Crédit total", "Full credit")
    else -> tr("Crédit partiel", "Partial credit")
}

// ---------------- STOCK ----------------
@Composable
fun StockScreen(vm: AppVm) {
    val list by vm.products.collectAsState()
    var edit by remember { mutableStateOf<Product?>(null) }
    var add by remember { mutableStateOf(false) }
    Screen(tr("Inventaire (Stock)", "Inventory"), { add = true }) {
        items(list, key = { it.id }) { p ->
            val u = maxOf(p.unitsPerCarton, 1)
            Card2({ edit = p }) {
                Text(p.name, fontWeight = FontWeight.Bold)
                Text("SKU: ${p.sku}  •  ${p.unitsPerCarton} " + tr("unités / carton", "units / carton"))
                KV(tr("Stock (unités)", "Stock (units)"), "${p.stock}")
                KV(tr("Équivalent", "Equivalent"), cartonsText(p))
                if (p.cartonsPerPallet > 0) KV(tr("Palettes", "Pallets"), String.format("%.2f", p.stock.toDouble() / (u * p.cartonsPerPallet)))
                KV(tr("Achat  unité | carton", "Cost  unit | carton"), "${p.purchasePrice.num()} | ${(p.purchasePrice * u).num()}")
                KV(tr("Gros  unité | carton", "Wholesale  unit | carton"), "${p.priceGros.num()} | ${(p.priceGros * u).num()}")
                KV(tr("DD/PDV  unité | carton", "Retailer  unit | carton"), "${p.priceDD.num()} | ${(p.priceDD * u).num()}")
                KV(tr("Public  unité | carton", "Retail  unit | carton"), "${p.salePrice.num()} | ${(p.salePrice * u).num()}")
                KV(tr("Valeur stock", "Stock value"), (p.stock * p.purchasePrice).fmt())
            }
        }
    }
    if (add) ProductDialog(null, vm) { add = false }
    edit?.let { ProductDialog(it, vm) { edit = null } }
}

@Composable
fun ProductDialog(p: Product?, vm: AppVm, close: () -> Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var sku by remember { mutableStateOf(p?.sku ?: "") }
    var stock by remember { mutableStateOf((p?.stock ?: 0).toString()) }
    var upc by remember { mutableStateOf((p?.unitsPerCarton ?: 1).toString()) }
    var cpp by remember { mutableStateOf((p?.cartonsPerPallet ?: 0).toString()) }
    var cost by remember { mutableStateOf((p?.purchasePrice ?: 0.0).num()) }
    var gros by remember { mutableStateOf((p?.priceGros ?: 0.0).num()) }
    var dd by remember { mutableStateOf((p?.priceDD ?: 0.0).num()) }
    var pub by remember { mutableStateOf((p?.salePrice ?: 0.0).num()) }
    Dlg(if (p == null) tr("Nouveau produit", "New product") else tr("Modifier produit", "Edit product"), close, {
        if (name.isNotBlank()) {
            vm.saveProduct(Product(id = p?.id ?: 0, name = name.trim(), sku = sku.trim(), stock = stock.i(),
                unitsPerCarton = maxOf(upc.i(), 1), cartonsPerPallet = cpp.i(), purchasePrice = cost.d(),
                priceGros = gros.d(), priceDD = dd.d(), salePrice = pub.d()))
            close()
        }
    }) {
        Field(tr("Nom", "Name"), name, { name = it })
        Field("SKU", sku, { sku = it })
        Field(tr("Stock (unités)", "Stock (units)"), stock, { stock = it }, true)
        Field(tr("Unités par carton (présentoir)", "Units per carton (display)"), upc, { upc = it }, true)
        Field(tr("Cartons par palette", "Cartons per pallet"), cpp, { cpp = it }, true)
        Text(tr("Prix PAR UNITÉ (DA)", "Prices PER UNIT (DA)"), fontWeight = FontWeight.Bold)
        Field(tr("Prix d'achat (distrib.)", "Purchase price"), cost, { cost = it }, true)
        Field(tr("Prix grossiste", "Wholesale price"), gros, { gros = it }, true)
        Field(tr("Prix DD / PDV", "Retailer price"), dd, { dd = it }, true)
        Field(tr("Prix public", "Retail price"), pub, { pub = it }, true)
        if (p != null) TextButton(onClick = { vm.deleteProduct(p); close() }) {
            Text(tr("Supprimer", "Delete"), color = Red)
        }
    }
}

// ---------------- PURCHASES ----------------
@Composable
fun PurchasesScreen(vm: AppVm) {
    val list by vm.purchases.collectAsState()
    var add by remember { mutableStateOf(false) }
    var sup by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf<Purchase?>(null) }
    Screen(tr("Achats fournisseurs", "Supplier purchases"), { add = true }) {
        item { OutlinedButton(onClick = { sup = true }) { Text(tr("Gérer les fournisseurs", "Manage suppliers")) } }
        items(list, key = { it.id }) { p ->
            val rem = p.total - p.amountPaid
            Card2(if (rem > 0.005) ({ pay = p }) else null) {
                Text(p.productName, fontWeight = FontWeight.Bold)
                Text("${fdate(p.date)}  •  ${p.supplier}")
                KV(tr("Quantité (unités)", "Quantity (units)"), "${p.qtyUnits}")
                if (p.lot.isNotBlank()) KV("Lot", p.lot + if (p.expiry.isNotBlank()) "  (exp. ${p.expiry})" else "")
                KV(tr("Total", "Total"), p.total.fmt())
                KV(tr("Payé", "Paid"), p.amountPaid.fmt())
                if (rem > 0.005) KV(tr("Reste à payer (toucher)", "Remaining (tap to pay)"), rem.fmt(), Red)
            }
        }
    }
    if (add) PurchaseDialog(vm) { add = false }
    if (sup) SupplierDialog(vm) { sup = false }
    pay?.let { p ->
        var amt by remember { mutableStateOf((p.total - p.amountPaid).num()) }
        Dlg(tr("Payer le fournisseur", "Pay supplier"), { pay = null }, { vm.payPurchase(p.id, amt.d()); pay = null }) {
            Field(tr("Montant", "Amount"), amt, { amt = it }, true)
        }
    }
}

@Composable
fun PurchaseDialog(vm: AppVm, close: () -> Unit) {
    val products by vm.products.collectAsState()
    val sups by vm.suppliers.collectAsState()
    var pi by remember { mutableStateOf(0) }
    var mi by remember { mutableStateOf(1) }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    var si by remember { mutableStateOf(0) }
    var lot by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    val p = products.getOrNull(pi)
    LaunchedEffect(pi, mi, products.size) {
        if (p != null) cost = (if (mi == 1) p.purchasePrice * p.unitsPerCarton else p.purchasePrice).num()
    }
    Dlg(tr("Nouvel achat", "New purchase"), close, {
        if (p != null && qty.i() > 0) {
            vm.purchase(p, if (mi == 1) "CARTON" else "UNIT", qty.i(), cost.d(), sups.getOrNull(si - 1)?.name ?: "",
                lot, exp, if (paid.isBlank()) null else paid.d())
            close()
        }
    }) {
        if (products.isEmpty()) Text(tr("Ajoutez d'abord un produit.", "Add a product first.")) else {
            Pick(tr("Produit", "Product"), products.map { it.name }, pi) { pi = it }
            Pick(tr("Lot / conditionnement", "Lot / pack"), modes(), mi) { mi = it }
            Field(tr("Quantité", "Quantity"), qty, { qty = it }, true)
            Field(tr("Prix d'achat par ", "Cost per ") + (if (mi == 1) "carton" else tr("unité", "unit")), cost, { cost = it }, true)
            Pick(tr("Fournisseur", "Supplier"), listOf("—") + sups.map { it.name }, si) { si = it }
            Field(tr("N° de lot (optionnel)", "Lot no. (optional)"), lot, { lot = it })
            Field(tr("Expiration (optionnel)", "Expiry (optional)"), exp, { exp = it })
            Field(tr("Montant payé (vide = tout)", "Amount paid (blank = all)"), paid, { paid = it }, true)
            val units = if (mi == 1) qty.i() * maxOf(p?.unitsPerCarton ?: 1, 1) else qty.i()
            Text("= $units " + tr("unités", "units") + "  •  Total: " + (qty.i() * cost.d()).fmt())
        }
    }
}

@Composable
fun SupplierDialog(vm: AppVm, close: () -> Unit) {
    val sups by vm.suppliers.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Dlg(tr("Fournisseurs", "Suppliers"), close, { if (name.isNotBlank()) vm.saveSupplier(Supplier(name = name.trim(), phone = phone)); close() }, tr("Ajouter", "Add")) {
        sups.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${s.name} ${s.phone}", Modifier.weight(1f))
                TextButton(onClick = { vm.deleteSupplier(s) }) { Text("✕", color = Red) }
            }
        }
        Field(tr("Nom", "Name"), name, { name = it })
        Field(tr("Téléphone", "Phone"), phone, { phone = it })
    }
}

// ---------------- SALES ----------------
@Composable
fun SalesScreen(vm: AppVm) {
    val list by vm.sales.collectAsState()
    var add by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Sale?>(null) }
    Screen(tr("Ventes & Crédit", "Sales & Credit"), { add = true }) {
        items(list, key = { it.id }) { s ->
            val rem = s.total - s.paid
            Card2({ settle = s }) {
                Text(s.customerName, fontWeight = FontWeight.Bold)
                Text(fdate(s.date) + "  •  " + status(s))
                KV(tr("Total", "Total"), s.total.fmt())
                KV(tr("Payé", "Paid"), s.paid.fmt(), Green)
                KV(tr("Crédit restant", "Credit remaining"), if (rem > 0.005) rem.fmt() else "0", if (rem > 0.005) Red else Green)
            }
        }
    }
    if (add) SaleDialog(vm) { add = false }
    settle?.let { SettleDialog(it, vm) { settle = null } }
}

@Composable
fun SaleDialog(vm: AppVm, close: () -> Unit) {
    val products by vm.products.collectAsState()
    val custs by vm.customers.collectAsState()
    var ci by remember { mutableStateOf(0) }
    var pi by remember { mutableStateOf(0) }
    var mi by remember { mutableStateOf(1) }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<LineIn>() }
    val cust = custs.getOrNull(ci - 1)
    val tier = cust?.tier ?: "PUBLIC"
    val p = products.getOrNull(pi)
    LaunchedEffect(pi, mi, ci, products.size) {
        if (p != null) price = (p.priceFor(tier) * (if (mi == 1) p.unitsPerCarton else 1)).num()
    }
    val total = lines.sumOf { it.total() }
    Dlg(tr("Nouvelle vente", "New sale"), close, {
        if (lines.isNotEmpty()) { vm.sell(cust, lines.toList(), if (paid.isBlank()) null else paid.d()); close() }
    }, tr("Valider", "Confirm")) {
        Pick(tr("Client", "Customer"), listOf(tr("Client comptoir", "Walk-in")) + custs.map { it.name }, ci) { ci = it }
        if (products.isEmpty()) Text(tr("Aucun produit.", "No products.")) else {
            Pick(tr("Produit", "Product"), products.map { it.name + " (${it.stock})" }, pi) { pi = it }
            Pick(tr("Lot / conditionnement", "Lot / pack"), modes(), mi) { mi = it }
            Field(tr("Quantité", "Quantity"), qty, { qty = it }, true)
            Field(tr("Prix par ", "Price per ") + (if (mi == 1) "carton" else tr("unité", "unit")), price, { price = it }, true)
            OutlinedButton(onClick = { if (p != null && qty.i() > 0) lines.add(LineIn(p, if (mi == 1) "CARTON" else "UNIT", qty.i(), price.d())) }) {
                Text("+ " + tr("Ajouter la ligne", "Add line"))
            }
        }
        lines.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${l.qty} ${if (l.mode == "CARTON") "ctn" else "u"} ${l.product.name}\n${l.total().fmt()}", Modifier.weight(1f))
                TextButton(onClick = { lines.removeAt(i) }) { Text("✕", color = Red) }
            }
        }
        Text("Total: ${total.fmt()}", fontWeight = FontWeight.Bold)
        Field(tr("Payé maintenant (vide = tout, 0 = crédit total)", "Paid now (blank = all, 0 = full credit)"), paid, { paid = it }, true)
    }
}

@Composable
fun SettleDialog(s: Sale, vm: AppVm, close: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    val rem = s.total - s.paid
    Dlg(tr("Règlement – ", "Settlement – ") + s.customerName, close, { if (amt.d() > 0) vm.settle(s.id, amt.d()); close() }) {
        KV(tr("Total", "Total"), s.total.fmt())
        KV(tr("Payé", "Paid"), s.paid.fmt(), Green)
        KV(tr("Reste", "Remaining"), rem.fmt(), if (rem > 0.005) Red else Green)
        if (rem > 0.005) {
            Field(tr("Montant versé", "Amount received"), amt, { amt = it }, true)
            TextButton(onClick = { amt = rem.num() }) { Text(tr("Tout solder", "Settle in full")) }
        }
    }
}

// ---------------- CUSTOMERS ----------------
val tiers = listOf("PUBLIC", "GROS", "DD")
fun tierLabels() = listOf(tr("Public", "Retail"), tr("Grossiste", "Wholesaler"), tr("Détaillant / PDV", "Retailer / POS"))

@Composable
fun CustomersScreen(vm: AppVm) {
    val list by vm.customers.collectAsState()
    val sales by vm.sales.collectAsState()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Customer?>(null) }
    Screen(tr("Clients", "Customers"), { add = true }) {
        items(list, key = { it.id }) { c ->
            val cs = sales.filter { it.customerId == c.id }
            Card2({ edit = c }) {
                Text(c.name, fontWeight = FontWeight.Bold)
                Text("${c.phone}  •  " + tierLabels()[tiers.indexOf(c.tier).coerceAtLeast(0)])
                KV(tr("Total acheté", "Total bought"), cs.sumOf { it.total }.fmt())
                KV(tr("Total payé", "Total paid"), cs.sumOf { it.paid }.fmt(), Green)
                val r = cs.sumOf { it.total - it.paid }
                KV(tr("Crédit restant", "Credit remaining"), r.fmt(), if (r > 0.005) Red else Green)
            }
        }
    }
    if (add) CustomerDialog(null, vm) { add = false }
    edit?.let { CustomerDialog(it, vm) { edit = null } }
}

@Composable
fun CustomerDialog(c: Customer?, vm: AppVm, close: () -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }
    var phone by remember { mutableStateOf(c?.phone ?: "") }
    var ti by remember { mutableStateOf(tiers.indexOf(c?.tier ?: "PUBLIC").coerceAtLeast(0)) }
    Dlg(tr("Client", "Customer"), close, {
        if (name.isNotBlank()) { vm.saveCustomer(Customer(id = c?.id ?: 0, name = name.trim(), phone = phone, tier = tiers[ti])); close() }
    }) {
        Field(tr("Nom", "Name"), name, { name = it })
        Field(tr("Téléphone", "Phone"), phone, { phone = it })
        Pick(tr("Niveau de prix", "Price tier"), tierLabels(), ti) { ti = it }
        if (c != null) TextButton(onClick = { vm.deleteCustomer(c); close() }) { Text(tr("Supprimer", "Delete"), color = Red) }
    }
}
