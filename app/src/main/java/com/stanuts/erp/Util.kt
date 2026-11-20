package com.stanuts.erp

import android.content.Context
import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.*

object Lang { var fr by mutableStateOf(true) }
fun tr(fr: String, en: String) = if (Lang.fr) fr else en
fun Double.fmt(): String = String.format(Locale.US, "%,.2f", this).replace(',', ' ') + " DA"
fun Double.num(): String = if (this == Math.rint(this)) toLong().toString() else String.format(Locale.US, "%.2f", this)
fun String.d(): Double = replace(',', '.').trim().toDoubleOrNull() ?: 0.0
fun String.i(): Int = trim().toIntOrNull() ?: 0
fun fdate(t: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(t))
object Prefs { fun get(c: Context) = c.getSharedPreferences("erp", Context.MODE_PRIVATE) }
