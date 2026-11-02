package com.zwallet.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwallet.app.data.Bank
import com.zwallet.app.data.CardCatalog
import com.zwallet.app.data.CardVariant
import com.zwallet.app.data.CardsStore
import com.zwallet.app.data.OwnedCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.absoluteValue

private fun routeDepth(r: String): Int = when {
    r == "home" -> 0
    r == "banks" || r.startsWith("detail/") -> 1
    else -> 2
}

private const val MAX_CARDS_PER_BANK = 5

private const val DISCLAIMER =
    "Semua kartu di ZWallet adalah kartu virtual/simulasi. Bukan kartu bank resmi dan tidak dapat dipakai untuk pembayaran nyata."

/** Halaman tab "Kartu": Kartu Saya, pilih bank, pratinjau kartu, dan detail kartu. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CardsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { CardsStore(context.applicationContext) }
    var cards by remember { mutableStateOf(store.loadCards()) }
    var primaryUid by remember { mutableStateOf(store.loadPrimary()) }
    var route by rememberSaveable { mutableStateOf("home") }
    var pendingAdd by remember { mutableStateOf<CardVariant?>(null) }
    var pendingDelete by remember { mutableStateOf<OwnedCard?>(null) }
    val holder = remember {
        context.getSharedPreferences("zwallet_prefs", Context.MODE_PRIVATE).getString("nickname", null).orEmpty()
    }
    val activePrimary = cards.firstOrNull { it.uid == primaryUid }?.uid ?: cards.firstOrNull()?.uid

    fun persist(newCards: List<OwnedCard>, newPrimary: String?) {
        cards = newCards
        primaryUid = newPrimary
        store.save(newCards, newPrimary)
    }

    BackHandler(enabled = route != "home") {
        route = if (route.startsWith("variants/")) "banks" else "home"
    }

    Box(modifier.background(ZBg)) {
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = routeDepth(targetState) > routeDepth(initialState)
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { w -> if (forward) -w / 5 else w / 5 } + fadeOut(tween(200)))
            },
            label = "cardsRoute"
        ) { r ->
            when {
                r == "banks" -> BankPicker(
                    counts = cards.groupingBy { it.bankId }.eachCount(),
                    onBack = { route = "home" },
                    onPick = { bank -> route = "variants/${bank.id}" }
                )
                r.startsWith("variants/") -> {
                    val bank = CardCatalog.bank(r.removePrefix("variants/"))
                    if (bank != null) {
                        BankCardPreview(
                            bank = bank,
                            ownedCount = cards.count { it.bankId == bank.id },
                            onBack = { route = "banks" },
                            onChoose = { v -> pendingAdd = v }
                        )
                    }
                }
                r.startsWith("detail/") -> {
                    val uid = r.removePrefix("detail/")
                    val card = cards.firstOrNull { it.uid == uid }
                    if (card != null) {
                        val same = cards.filter { it.bankId == card.bankId }
                        CardDetail(
                            card = card,
                            ordinal = same.indexOfFirst { it.uid == card.uid } + 1,
                            sameBankCount = same.size,
                            holder = holder,
                            isPrimary = card.uid == activePrimary,
                            onBack = { route = "home" },
                            onMakePrimary = { persist(cards, card.uid) },
                            onDelete = { pendingDelete = card }
                        )
                    }
                }
                else -> CardsHome(
                    cards = cards,
                    primaryUid = activePrimary,
                    onAdd = { route = "banks" },
                    onOpen = { c -> route = "detail/${c.uid}" },
                    onDelete = { c -> pendingDelete = c }
                )
            }
        }
    }

    pendingAdd?.let { v ->
        AlertDialog(
            onDismissRequest = { pendingAdd = null },
            containerColor = ZCard,
            titleContentColor = ZText,
            textContentColor = ZMuted,
            title = { Text("Tambahkan kartu ini?") },
            text = { Text("${v.name} akan ditambahkan ke Kartu Saya sebagai kartu virtual (simulasi).") },
            confirmButton = {
                TextButton(onClick = {
                    if (cards.count { it.bankId == v.bankId } < MAX_CARDS_PER_BANK) {
                        val card = OwnedCard(UUID.randomUUID().toString(), v.bankId, v.id, System.currentTimeMillis())
                        persist(cards + card, if (cards.isEmpty()) card.uid else activePrimary)
                    }
                    pendingAdd = null
                    route = "home"
                }) { Text("Tambahkan", color = ZGold, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingAdd = null }) { Text("Batal", color = ZMuted) } }
        )
    }

    pendingDelete?.let { c ->
        val name = CardCatalog.variant(c.variantId)?.name ?: "kartu ini"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ZCard,
            titleContentColor = ZText,
            textContentColor = ZMuted,
            title = { Text("Hapus kartu?") },
            text = { Text("$name akan dihapus dari Kartu Saya. Saldo dan transaksi di Wallet tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = {
                    val rest = cards.filter { it.uid != c.uid }
                    persist(rest, if (activePrimary == c.uid) rest.firstOrNull()?.uid else activePrimary)
                    pendingDelete = null
                    if (route == "detail/${c.uid}") route = "home"
                }) { Text("Hapus", color = ZError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal", color = ZMuted) } }
        )
    }
}

// ---------------------------------------------------------------- Kartu Saya

@Composable
private fun CardsHome(
    cards: List<OwnedCard>,
    primaryUid: String?,
    onAdd: () -> Unit,
    onOpen: (OwnedCard) -> Unit,
    onDelete: (OwnedCard) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("KARTU", color = ZGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Kartu Saya", color = ZText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Koleksi kartu virtual milikmu", color = ZMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                AddCardButton(onAdd)
            }
        }
        if (cards.isEmpty()) {
            item(key = "empty") { EmptyCards(onAdd) }
        }
        items(cards, key = { it.uid }) { card ->
            val same = cards.filter { it.bankId == card.bankId }
            OwnedCardTile(
                card = card,
                ordinal = if (same.size > 1) same.indexOfFirst { it.uid == card.uid } + 1 else 0,
                isPrimary = card.uid == primaryUid,
                onOpen = { onOpen(card) },
                onDelete = { onDelete(card) },
                modifier = Modifier.animateItem()
            )
        }
        item(key = "disclaimer") {
            Text(DISCLAIMER, color = ZMuted, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun AddCardButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ZGold, contentColor = ZOnGold),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Tambah Kartu", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyCards(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ZCard).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.CreditCard, null, tint = ZMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Belum ada kartu", color = ZText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pilih bank untuk menambahkan kartu virtual pertamamu.",
            color = ZMuted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = ZGold, contentColor = ZOnGold)
        ) { Text("Pilih Bank", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PrimaryBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(ZGold.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Star, null, tint = ZGold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("Kartu Utama", color = ZGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OwnedCardTile(
    card: OwnedCard,
    ordinal: Int,
    isPrimary: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val variant = CardCatalog.variant(card.variantId) ?: return
    val bank = CardCatalog.bank(card.bankId)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ZCard)
            .clickable(onClick = onOpen).padding(14.dp)
    ) {
        CardImage(variant, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bank?.name.orEmpty(), color = ZMuted, fontSize = 12.sp)
                Text(
                    variant.name + if (ordinal > 0) "  #$ordinal" else "",
                    color = ZText, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
            if (isPrimary) PrimaryBadge()
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(ZSuccess))
            Spacer(Modifier.width(7.dp))
            Text("Aktif • Kartu virtual", color = ZMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Hapus kartu", tint = ZMuted) }
            TextButton(onClick = onOpen) { Text("Detail", color = ZGold, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- Pilih bank

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = ZText) }
        Column {
            Text(title, color = ZText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = ZMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BankPicker(counts: Map<String, Int>, onBack: () -> Unit, onPick: (Bank) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pilih Bank", "Maksimal $MAX_CARDS_PER_BANK kartu per bank", onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(CardCatalog.banks, key = { it.id }) { bank ->
                BankTile(bank, owned = counts[bank.id] ?: 0, onClick = { onPick(bank) })
            }
        }
    }
}

@Composable
private fun BankTile(bank: Bank, owned: Int, onClick: () -> Unit) {
    val logo by rememberAssetImage("logos", bank.id, 400)
    val bmp = logo?.bitmap
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ZCard)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(14.dp))
                .background(if (bmp != null) Color(0xFFF5F5F5) else ZCard2),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = bank.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp))
            } else {
                Text(bank.initials, color = ZGold, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(bank.name, color = ZText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(if (owned == 0) "Belum ada kartu" else "$owned dari $MAX_CARDS_PER_BANK kartu", color = ZMuted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- Pratinjau kartu bank

@Composable
private fun BankCardPreview(bank: Bank, ownedCount: Int, onBack: () -> Unit, onChoose: (CardVariant) -> Unit) {
    val variant = remember(bank.id) { CardCatalog.variantsOf(bank.id).first() }
    val full = ownedCount >= MAX_CARDS_PER_BANK

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenHeader(bank.name, "Pratinjau kartu", onBack)
            Spacer(Modifier.height(16.dp))
            CardImage(variant, Modifier.fillMaxWidth().padding(horizontal = 28.dp), maxWidth = 1100)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(variant.name, color = ZText, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(variant.description, color = ZMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Kartu virtual • simulasi", color = ZGold, fontSize = 11.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (full) "Batas $MAX_CARDS_PER_BANK kartu ${bank.name} sudah tercapai. Hapus salah satu kartu untuk menambah yang baru."
                    else "Kamu punya $ownedCount dari $MAX_CARDS_PER_BANK kartu ${bank.name}. Semua kartu ${bank.name} memakai tampilan yang sama.",
                    color = ZMuted, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack, modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ZCard2)
            ) { Text("Kembali", color = ZText) }
            Button(
                onClick = { onChoose(variant) }, enabled = !full, modifier = Modifier.weight(1.4f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZGold, contentColor = ZOnGold,
                    disabledContainerColor = ZCard2, disabledContentColor = ZMuted
                )
            ) { Text("Pilih Kartu", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- Detail kartu

@Composable
private fun CardDetail(
    card: OwnedCard,
    ordinal: Int,
    sameBankCount: Int,
    holder: String,
    isPrimary: Boolean,
    onBack: () -> Unit,
    onMakePrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val variant = CardCatalog.variant(card.variantId) ?: return
    val bank = CardCatalog.bank(card.bankId)
    val date = remember(card.addedAt) {
        SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(card.addedAt))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Detail Kartu", variant.name, onBack)
        Spacer(Modifier.height(8.dp))
        CardImage(variant, Modifier.fillMaxWidth().padding(horizontal = 28.dp), maxWidth = 1100)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(ZCard).padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            InfoRow("Bank", bank?.name.orEmpty())
            InfoRow("Jenis kartu", variant.name)
            if (sameBankCount > 1) InfoRow("Kartu ke", "$ordinal dari $sameBankCount")
            InfoRow("Status", "Aktif • Virtual")
            if (holder.isNotBlank()) InfoRow("Pemilik", holder)
            InfoRow("Ditambahkan", date)
            InfoRow("Kartu utama", if (isPrimary) "Ya" else "Bukan", last = true)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isPrimary) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PrimaryBadge() }
            } else {
                Button(
                    onClick = onMakePrimary, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ZGold, contentColor = ZOnGold)
                ) {
                    Icon(Icons.Rounded.StarBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jadikan Kartu Utama", fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(
                onClick = onDelete, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ZError.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Rounded.Delete, null, tint = ZError, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hapus Kartu", color = ZError)
            }
        }
        Text(DISCLAIMER, color = ZMuted, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ZMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = ZText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    if (!last) HorizontalDivider(color = ZCard2)
}
