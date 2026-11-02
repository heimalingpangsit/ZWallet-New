package com.zwallet.app.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwallet.app.assistant.AssistantEngine
import com.zwallet.app.assistant.Categories
import com.zwallet.app.assistant.ChatMessage
import com.zwallet.app.assistant.ChatStore
import com.zwallet.app.assistant.Ctx
import com.zwallet.app.assistant.Fmt
import com.zwallet.app.assistant.Quick
import com.zwallet.app.data.WalletCard
import com.zwallet.app.data.WalletTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INLINE_TX = 5

/** Tab ZWallet Assistant: chat offline yang menjawab dari data transaksi di perangkat. */
@Composable
fun AssistantScreen(
    cards: List<WalletCard>,
    transactions: List<WalletTransaction>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { ChatStore(context.applicationContext) }
    val nickname = remember {
        context.getSharedPreferences("zwallet_prefs", Context.MODE_PRIVATE).getString("nickname", null).orEmpty()
    }
    val messages = remember { mutableStateListOf<ChatMessage>().also { it.addAll(store.load()) } }
    var input by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var detailTx by remember { mutableStateOf<WalletTransaction?>(null) }
    var listTxs by remember { mutableStateOf<List<WalletTransaction>?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val txById = remember(transactions) { transactions.associateBy { it.id } }
    val cardNames = remember(cards) { cards.associate { it.id to it.name } }

    fun nextId(): Long = maxOf(System.currentTimeMillis(), (messages.lastOrNull()?.id ?: 0L) + 1L)

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || typing) return
        val last = messages.lastOrNull { !it.fromUser && it.ctxIntent != null }
        val ctx = last?.let { Ctx(it.ctxIntent.orEmpty(), it.ctxPeriod, it.ctxType, it.ctxCategory) }
        messages.add(ChatMessage(nextId(), true, text, System.currentTimeMillis()))
        store.save(messages)
        input = ""
        typing = true
        scope.launch {
            delay(400)
            val reply = withContext(Dispatchers.Default) {
                try {
                    AssistantEngine(cards, transactions, nickname).answer(text, ctx)
                } catch (e: Exception) {
                    null
                }
            }
            if (reply != null) {
                messages.add(
                    ChatMessage(
                        id = nextId(), fromUser = false, text = reply.text, time = System.currentTimeMillis(),
                        txIds = reply.txIds, chips = reply.chips,
                        ctxIntent = reply.ctx?.intent, ctxPeriod = reply.ctx?.period,
                        ctxType = reply.ctx?.type, ctxCategory = reply.ctx?.category
                    )
                )
            } else {
                messages.add(ChatMessage(nextId(), false, "Maaf, ada kendala saat menghitung jawabannya. Coba tanya dengan cara lain ya.", System.currentTimeMillis()))
            }
            typing = false
            store.save(messages)
        }
    }

    val itemCount = (if (messages.isEmpty()) 1 else messages.size) + (if (typing) 1 else 0)
    LaunchedEffect(messages.size, typing) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier.background(ZBg)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ZWALLET ASSISTANT", color = ZGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Tanya keuanganmu", color = ZText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "OFFLINE", color = ZSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(ZCard2).padding(horizontal = 12.dp, vertical = 7.dp)
            )
            if (messages.isNotEmpty()) {
                IconButton(onClick = { showClear = true }) { Icon(Icons.Rounded.Delete, "Hapus riwayat chat", tint = ZMuted) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "intro") { IntroCard(nickname) }
            }
            items(messages, key = { it.id }) { m ->
                if (m.fromUser) {
                    UserBubble(m)
                } else {
                    val txs = m.txIds.mapNotNull { txById[it] }
                    BotBubble(
                        m = m, txs = txs, cardNames = cardNames,
                        showChips = m.id == messages.lastOrNull()?.id && !typing,
                        onChip = { send(it) },
                        onTx = { detailTx = it },
                        onAll = { listTxs = it }
                    )
                }
            }
            if (typing) item(key = "typing") { TypingBubble() }
        }

        // Quick prompts
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Quick.prompts) { p -> PromptChip(p) { send(p) } }
        }

        // Input
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tanya soal keuanganmu…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ZText, unfocusedTextColor = ZText,
                    focusedBorderColor = ZGold, unfocusedBorderColor = ZCard2,
                    focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                    cursorColor = ZGold,
                    focusedPlaceholderColor = ZMuted, unfocusedPlaceholderColor = ZMuted
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !typing,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ZGold, contentColor = ZOnGold,
                    disabledContainerColor = ZCard2, disabledContentColor = ZMuted
                )
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim") }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = ZCard, titleContentColor = ZText, textContentColor = ZMuted,
            title = { Text("Hapus riwayat chat?") },
            text = { Text("Semua percakapan dengan ZWallet Assistant di perangkat ini akan dihapus. Data transaksimu tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = { messages.clear(); store.clear(); showClear = false }) {
                    Text("Hapus", color = ZError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Batal", color = ZMuted) } }
        )
    }

    listTxs?.let { list ->
        AlertDialog(
            onDismissRequest = { listTxs = null },
            containerColor = ZCard, titleContentColor = ZText, textContentColor = ZMuted,
            title = { Text("${list.size} transaksi") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { tx ->
                        TxRow(tx, cardNames[tx.cardId] ?: "kartu terhapus") { detailTx = tx }
                        HorizontalDivider(color = ZCard2)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { listTxs = null }) { Text("Tutup", color = ZGold, fontWeight = FontWeight.Bold) } }
        )
    }

    detailTx?.let { tx ->
        TxDetailDialog(tx, cardNames[tx.cardId] ?: "kartu terhapus") { detailTx = null }
    }
}

// ---------------------------------------------------------------- bubbles

private fun rich(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZGold)) { append(part) } else append(part)
    }
}

@Composable
private fun Avatar() {
    Box(Modifier.size(30.dp).clip(CircleShape).background(ZGold), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = ZOnGold, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            m.text, color = ZOnGold, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(ZGold)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Text(Fmt.hm(m.time), color = ZMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp, end = 4.dp))
    }
}

@Composable
private fun BotBubble(
    m: ChatMessage,
    txs: List<WalletTransaction>,
    cardNames: Map<Long, String>,
    showChips: Boolean,
    onChip: (String) -> Unit,
    onTx: (WalletTransaction) -> Unit,
    onAll: (List<WalletTransaction>) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Avatar()
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                rich(m.text), color = ZText, fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).background(ZCard)
                    .border(1.dp, ZGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            )
            if (txs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(ZCard)
                        .border(1.dp, ZCard2, RoundedCornerShape(16.dp)).padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        "RINCIAN TRANSAKSI (${txs.size})", color = ZGold, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    txs.take(INLINE_TX).forEachIndexed { i, tx ->
                        if (i > 0) HorizontalDivider(color = ZCard2, modifier = Modifier.padding(horizontal = 8.dp))
                        TxRow(tx, cardNames[tx.cardId] ?: "kartu terhapus") { onTx(tx) }
                    }
                    if (txs.size > INLINE_TX) {
                        TextButton(onClick = { onAll(txs) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Lihat semua ${txs.size} transaksi", color = ZGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (showChips && m.chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                    items(m.chips) { c -> PromptChip(c) { onChip(c) } }
                }
            }
            Text(Fmt.hm(m.time), color = ZMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp, start = 4.dp))
        }
    }
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val a by t.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar()
        Spacer(Modifier.width(8.dp))
        Text(
            "● ● ●", color = ZGold, fontSize = 11.sp,
            modifier = Modifier.alpha(a).clip(RoundedCornerShape(18.dp)).background(ZCard)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun IntroCard(nickname: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ZCard)
            .border(1.dp, ZGold.copy(alpha = 0.2f), RoundedCornerShape(22.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(ZGold), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = ZOnGold, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(if (nickname.isBlank()) "Halo! Aku ZWallet Assistant" else "Halo $nickname! Aku ZWallet Assistant",
            color = ZText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tanya apa aja soal saldo, pemasukan, pengeluaran, atau kartumu, pakai bahasa santai juga boleh. " +
                "Jawabanku dihitung langsung dari data di HP ini, tanpa internet dan tanpa server.",
            color = ZMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text("Coba tap salah satu saran di bawah 👇", color = ZGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PromptChip(text: String, onClick: () -> Unit) {
    Text(
        text, color = ZGold, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(ZGold.copy(alpha = 0.10f))
            .border(1.dp, ZGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------- transaksi

@Composable
private fun TxRow(tx: WalletTransaction, cardName: String, onClick: () -> Unit) {
    val income = tx.type == "INCOME"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(ZCard2), contentAlignment = Alignment.Center) {
            Icon(
                if (income) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null,
                tint = if (income) ZSuccess else ZError, modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.note, color = ZText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$cardName • ${Fmt.date(tx.createdAt)}", color = ZMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "-") + Fmt.rupiah(tx.amount),
            color = if (income) ZSuccess else ZError, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TxDetailDialog(tx: WalletTransaction, cardName: String, onDismiss: () -> Unit) {
    val income = tx.type == "INCOME"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZCard, titleContentColor = ZText, textContentColor = ZMuted,
        title = { Text("Detail transaksi") },
        text = {
            Column {
                Text(
                    (if (income) "+" else "-") + Fmt.rupiah(tx.amount),
                    color = if (income) ZSuccess else ZError, fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                DetailLine("Keterangan", tx.note)
                DetailLine("Jenis", if (income) "Pemasukan" else "Pengeluaran")
                DetailLine("Kartu", cardName)
                DetailLine("Kategori", Categories.of(tx))
                DetailLine("Waktu", Fmt.full(tx.createdAt), last = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = ZGold, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun DetailLine(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(label, color = ZMuted, fontSize = 13.sp, modifier = Modifier.width(88.dp))
        Text(value, color = ZText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
    if (!last) HorizontalDivider(color = ZCard2)
}
