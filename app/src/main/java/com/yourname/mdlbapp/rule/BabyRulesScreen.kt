import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.R
import com.yourname.mdlbapp.core.ui.AppHeightClass
import com.yourname.mdlbapp.core.ui.AppWidthClass
import com.yourname.mdlbapp.core.ui.rememberAppHeightClass
import com.yourname.mdlbapp.core.ui.rememberAppWidthClass
import com.yourname.mdlbapp.core.ui.rememberIsLandscape
import com.yourname.mdlbapp.rule.Rule
import com.yourname.mdlbapp.rule.RuleCard

// ——— Адаптивные токены для экрана правил
private data class RulesUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val backIcon: Dp,
    val backText: Float,
    val titleSize: Float,
    val emptySize: Float,
    val listGap: Dp,
    val bottomPad: Dp
)

@Composable
private fun rememberRulesUiTokens(): RulesUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()

    val phonePortrait  = !landscape && w == AppWidthClass.Compact
    val phoneLandscape =  landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet         = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 800.dp
        AppWidthClass.Medium  -> 680.dp
        else -> if (phoneLandscape) 560.dp else 520.dp
    }
    val hPad = when {
        phoneLandscape -> 12.dp
        phonePortrait  -> 16.dp
        else -> 20.dp
    }
    val vPad = when {
        phoneLandscape -> 10.dp
        phonePortrait  -> 18.dp
        else -> 22.dp
    }
    val gap = if (phoneLandscape) 8.dp else 12.dp
    val listGap = if (phoneLandscape) 10.dp else 12.dp
    val bottomPad = if (phoneLandscape) 12.dp else 20.dp

    val backIcon = when {
        tablet -> 41.dp
        phoneLandscape -> 35.dp
        else -> 37.dp
    }
    val backText = when {
        tablet -> 18f
        phoneLandscape -> 16f
        else -> 17f
    }

    val titleSize = when {
        tablet -> 26f
        phoneLandscape -> 22f
        else -> 24f
    }
    val emptySize = when {
        tablet -> 18f
        phoneLandscape -> 16f
        else -> 17f
    }

    return RulesUiTokens(
        contentMaxWidth, hPad, vPad, gap,
        backIcon, backText, titleSize, emptySize, listGap, bottomPad
    )
}

@Composable
fun BabyRulesScreen(navController: NavHostController) {
    val t = rememberRulesUiTokens()
    val rules = remember { mutableStateListOf<Rule>() }
    var mommyUid by remember { mutableStateOf<String?>(null) }

    // 1) Подгружаем UID Мамочки
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("users").document(babyUid).get()
            .addOnSuccessListener { doc -> mommyUid = doc.getString("pairedWith") }
    }

    // 2) Подписка на правила Мамочки
    LaunchedEffect(mommyUid) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val mUid = mommyUid ?: return@LaunchedEffect
        Firebase.firestore.collection("rules")
            .whereEqualTo("targetUid", babyUid)
            .whereEqualTo("createdBy", mUid)
            .orderBy("createdAt")
            .addSnapshotListener { snaps, e ->
                if (e != null) return@addSnapshotListener
                rules.clear()
                snaps?.documents?.forEach { d ->
                    d.toObject(Rule::class.java)?.also {
                        it.id = d.id
                        rules.add(it)
                    }
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .widthIn(max = t.contentMaxWidth)
                .padding(horizontal = t.hPad, vertical = t.vPad)
        ) {
            // 🔝 Единая шапка: стрелка + центрированный заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // слева — только стрелочка (крупная зона тапа)
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(48.dp) // контейнер тапа
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Назад",
                        tint = Color(0xFF552216),
                        modifier = Modifier.size(t.backIcon) // сам размер стрелки из токенов
                    )
                }

                // по центру — заголовок, всегда одной строкой
                Text(
                    text = "📜 Правила Мамочки",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = t.titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF552216),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // справа — пустышка той же ширины, чтобы заголовок был ИСТИННО по центру
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(t.gap))

            if (rules.isEmpty()) {
                Text(
                    text = "Пока нет правил...\nЖди указаний 🕊",
                    fontStyle = FontStyle.Italic,
                    fontSize = t.emptySize.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(t.listGap),
                    contentPadding = PaddingValues(bottom = t.bottomPad),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(rules) { index, rule ->
                        RuleCard(
                            number = index + 1,
                            rule = rule,
                            onDelete = {},   // Малыш не удаляет
                            onEdit = {}      // и не редактирует
                        )
                    }
                }
            }
        }
    }
}