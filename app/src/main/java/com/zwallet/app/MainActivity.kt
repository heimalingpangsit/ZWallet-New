package com.zwallet.app

import android.os.Bundle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zwallet.app.data.*
import com.zwallet.app.ui.AssistantScreen
import com.zwallet.app.ui.CardImage
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B0C10)
private val Surface = Color(0xFF171920)
private val Surface2 = Color(0xFF22242D)
private val Gold = Color(0xFFD4AF37)
private val OnGold = Color(0xFF14110A)
private val Accent = Color(0xFFE34B55) // merah: pengeluaran / error
private val Muted = Color(0xFF92949D)
private val Green = Color(0xFF35C98A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = WalletDatabase.get(this).walletDao()
        val prefs = getSharedPreferences("zwallet_prefs", MODE_PRIVATE)
        val savedName = prefs.getString("nickname", null)?.takeIf { it.isNotBlank() }
        setContent {
            val vm: WalletViewModel = viewModel(factory = WalletViewModel.factory(dao))
            var showWelcome by remember { mutableStateOf(true) }
            ZWalletTheme {
                Crossfade(targetState = showWelcome, animationSpec = tween(500), label = "root") { welcome ->
                    if (welcome) {
                        WelcomeScreen(
                            savedName = savedName,
                            onNameSaved = { prefs.edit().putString("nickname", it).apply() },
                            onFinished = { showWelcome = false }
                        )
                    } else {
                        WalletScreen(vm)
                    }
                }
            }
        }
    }
}

class WalletViewModel(private val dao: WalletDao) : ViewModel() {
    val cards = dao.observeCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCard(name: String, bank: String, balance: Long) = viewModelScope.launch {
        if (name.isNotBlank() && balance >= 0) dao.insertCard(WalletCard(name = name.trim(), bank = bank, balance = balance))
    }

    fun addTransaction(card: WalletCard, type: String, amount: Long, note: String) = viewModelScope.launch {
        if (amount <= 0) return@launch
        if (type == "EXPENSE" && amount > card.balance) return@launch
        dao.insertTransaction(WalletTransaction(cardId = card.id, type = type, amount = amount, note = note.trim().ifBlank { if (type == "INCOME") "Pemasukan" else "Pengeluaran" }))
        dao.changeBalance(card.id, if (type == "INCOME") amount else -amount)
    }

    fun deleteCard(card: WalletCard) = viewModelScope.launch { dao.deleteCard(card) }

    companion object {
        fun factory(dao: WalletDao) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletViewModel(dao) as T
        }
    }
}

@Composable
private fun ZWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Surface, surfaceContainerHigh = Surface, surfaceContainer = Surface,
            primary = Gold, onPrimary = OnGold, secondary = Gold,
            secondaryContainer = Color(0xFF3A3220), onSecondaryContainer = Gold,
            surfaceVariant = Surface2, outline = Color(0xFF3A3D48), outlineVariant = Surface2,
            onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun WelcomeScreen(savedName: String?, onNameSaved: (String) -> Unit, onFinished: () -> Unit) {
    var name by remember { mutableStateOf(savedName) }
    var input by remember { mutableStateOf("") }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val inputAlpha = remember { Animatable(0f) }
    val greetAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(700)) }
        logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        if (savedName == null) inputAlpha.animateTo(1f, tween(500))
    }
    LaunchedEffect(name) {
        if (name != null) {
            greetAlpha.snapTo(0f)
            greetAlpha.animateTo(1f, tween(800))
            delay(1500)
            onFinished()
        }
    }

    val submit: () -> Unit = {
        val n = input.trim()
        if (n.isNotEmpty()) { onNameSaved(n); name = n }
    }

    Box(
        Modifier.fillMaxSize().background(Bg)
            .background(Brush.radialGradient(listOf(Color(0x33D4AF37), Color.Transparent))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.zwallet_logo),
                contentDescription = "ZWallet",
                modifier = Modifier.size(if (name == null) 170.dp else 210.dp).graphicsLayer {
                    alpha = logoAlpha.value; scaleX = logoScale.value; scaleY = logoScale.value
                }
            )
            Spacer(Modifier.height(28.dp))
            if (name == null) {
                Column(
                    Modifier.graphicsLayer { alpha = inputAlpha.value; translationY = (1f - inputAlpha.value) * 30f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Selamat datang di ZWallet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Siapa nama panggilanmu?", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { if (it.length <= 20) input = it },
                        label = { Text("Nama panggilan") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { submit() }, enabled = input.isNotBlank() && inputAlpha.value > 0.9f) { Text("Lanjut") }
                }
            } else {
                Column(
                    Modifier.graphicsLayer { alpha = greetAlpha.value; translationY = (1f - greetAlpha.value) * 40f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (savedName != null) "Welcome Back" else "Welcome",
                        color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(name ?: "", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WalletScreen(vm: WalletViewModel) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    var showAddCard by remember { mutableStateOf(false) }
    var showTransaction by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<WalletCard?>(null) }
    var pendingDelete by remember { mutableStateOf<WalletCard?>(null) }
    var tab by remember { mutableStateOf(0) }
    val total = cards.sumOf { it.balance }
    val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }

    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            if (tab != 1) FloatingActionButton(
                onClick = { if (cards.isNotEmpty()) { selectedCard = cards.first(); showTransaction = true } else showAddCard = true },
                containerColor = Gold, contentColor = OnGold
            ) { Icon(Icons.Rounded.Add, contentDescription = "Tambah transaksi") }
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gold, selectedTextColor = Gold,
                    indicatorColor = Color(0xFF3A3220),
                    unselectedIconColor = Muted, unselectedTextColor = Muted
                )
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = itemColors, icon = { Icon(Icons.Rounded.Wallet, null) }, label = { Text("Wallet") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = itemColors, icon = { Icon(Icons.Rounded.AutoAwesome, null) }, label = { Text("Assistant") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, colors = itemColors, icon = { Icon(Icons.Rounded.ArrowUpward, null) }, label = { Text("Aktivitas") })
            }
        }
    ) { padding ->
        if (tab == 1) AssistantScreen(
            cards = cards, transactions = transactions,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
        ) else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ZWALLET", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(if (tab == 2) "Aktivitas" else "Ringkasan wallet", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("OFFLINE", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Surface2, RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            if (tab == 0) {
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp))
                            .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(24.dp)).padding(22.dp)
                    ) {
                        Text("TOTAL SALDO", color = Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(rupiah(total), color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniStat("Pemasukan", income, Green, Modifier.weight(1f))
                            MiniStat("Pengeluaran", expense, Accent, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kartu tersimpan", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showAddCard = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = Gold)
                            Text("Tambah", color = Gold, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (cards.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CreditCard, null, tint = Gold, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Belum ada kartu", fontWeight = FontWeight.SemiBold)
                            Text("Tambahkan kartu dan masukkan saldo saat ini.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showAddCard = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)) { Text("Tambah kartu pertama", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                items(cards, key = { it.id }) { card ->
                    CardItem(card, onDelete = { pendingDelete = card }, onIncome = { selectedCard = card; showTransaction = true }, onExpense = { selectedCard = card; showTransaction = true })
                }
            }
            item { Text("Transaksi terbaru", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (transactions.isEmpty()) item { Text("Belum ada transaksi.", color = Muted, fontSize = 13.sp) }
            items(transactions.take(30), key = { it.id }) { tx ->
                val cardName = cards.firstOrNull { it.id == tx.cardId }?.name ?: "Kartu dihapus"
                TransactionRow(tx, cardName)
            }
            item {
                Text(
                    "Credit: JEPIN666X",
                    color = Muted, fontSize = 11.sp, letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showAddCard) AddCardDialog(onDismiss = { showAddCard = false }, onSave = { name, bank, balance -> vm.addCard(name, bank, balance); showAddCard = false })
    if (showTransaction) TransactionDialog(
        cards = cards, initialCard = selectedCard,
        onDismiss = { showTransaction = false },
        onSave = { card, type, amount, note -> vm.addTransaction(card, type, amount, note); showTransaction = false }
    )
    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus kartu?") },
            text = { Text("${c.name} beserta transaksinya akan dihapus.", color = Muted) },
            confirmButton = { TextButton(onClick = { vm.deleteCard(c); pendingDelete = null }) { Text("Hapus", color = Accent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal", color = Muted) } }
        )
    }
}

@Composable
private fun MiniStat(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier.background(Surface2, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(rupiah(amount), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardItem(card: WalletCard, onDelete: () -> Unit, onIncome: () -> Unit, onExpense: () -> Unit) {
    val goldBorder = BorderStroke(1.dp, Gold.copy(alpha = 0.6f))
    Column(
        Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(22.dp))
            .border(1.dp, Gold.copy(alpha = 0.18f), RoundedCornerShape(22.dp)).padding(14.dp)
    ) {
        CardImage(CardCatalog.assetFor(card.bank), card.bank, Modifier.fillMaxWidth(), maxWidth = 900)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.bank, color = Muted, fontSize = 12.sp)
                Text(card.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Hapus kartu", tint = Muted) }
        }
        Spacer(Modifier.height(10.dp))
        Text("SALDO SAAT INI", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(rupiah(card.balance), color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onIncome, modifier = Modifier.weight(1f), border = goldBorder) {
                Icon(Icons.Rounded.ArrowDownward, null, tint = Green, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Pemasukan", fontSize = 12.sp, color = Color.White)
            }
            OutlinedButton(onClick = onExpense, modifier = Modifier.weight(1f), border = goldBorder) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = Accent, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Pengeluaran", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction, cardName: String) {
    val isIncome = tx.type == "INCOME"
    Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Surface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(if (isIncome) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null, tint = if (isIncome) Green else Accent)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.note, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("$cardName • ${SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(Date(tx.createdAt))}", color = Muted, fontSize = 11.sp)
        }
        Text((if (isIncome) "+" else "-") + rupiah(tx.amount), color = if (isIncome) Green else Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    val presets = CardCatalog.presets
    var bank by remember { mutableStateOf(presets.first().label) }
    var name by remember { mutableStateOf(presets.first().label) }
    var balance by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah kartu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Crossfade(targetState = bank, animationSpec = tween(250), label = "cardPreview") { b ->
                    CardImage(CardCatalog.assetFor(b), b, Modifier.fillMaxWidth(), maxWidth = 700)
                }
                Text("Pilih bank / dompet digital", color = Muted, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        val sel = p.label == bank
                        Text(
                            p.label,
                            color = if (sel) OnGold else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Gold else Surface2)
                                .clickable { bank = p.label; if (p.label != "Lainnya") name = p.label }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama kartu / rekening") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = balance, onValueChange = { balance = it.filter(Char::isDigit) }, label = { Text("Saldo sekarang (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, bank, balance.toLongOrNull() ?: 0L) }, enabled = name.isNotBlank() && (balance.toLongOrNull() ?: -1L) >= 0) { Text("Simpan", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Muted) } }
    )
}

@Composable
private fun TransactionDialog(
    cards: List<WalletCard>, initialCard: WalletCard?,
    onDismiss: () -> Unit, onSave: (WalletCard, String, Long, String) -> Unit
) {
    var selectedId by remember { mutableLongStateOf(initialCard?.id ?: cards.firstOrNull()?.id ?: -1L) }
    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val selected = cards.firstOrNull { it.id == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat transaksi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Pemasukan") })
                    FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Pengeluaran") })
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected?.name ?: "Pilih kartu"); Spacer(Modifier.weight(1f)); Text("⌄") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        cards.forEach { c -> DropdownMenuItem(text = { Text("${c.name} • ${rupiah(c.balance)}") }, onClick = { selectedId = c.id; expanded = false }) }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit) }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan") }, singleLine = true)
                if (type == "EXPENSE" && selected != null && (amount.toLongOrNull() ?: 0L) > selected.balance) {
                    Text("Saldo kartu tidak mencukupi.", color = Accent, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selected != null) onSave(selected, type, amount.toLongOrNull() ?: 0L, note) },
                enabled = selected != null && (amount.toLongOrNull() ?: 0L) > 0 && !(type == "EXPENSE" && (amount.toLongOrNull() ?: 0L) > selected.balance)
            ) { Text("Simpan transaksi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Muted) } }
    )
}

private fun rupiah(value: Long): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
