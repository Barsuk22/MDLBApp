@file:OptIn(ExperimentalMaterial3Api::class)
package com.app.mdlbapp.authorization   // ← поменяй на свой пакет!

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.R


// ---- Токены внешнего вида
private data class RoleUiTokens(
    val cardHeight: Dp,
    val cardCorner: Dp,
    val cardBorder: Dp,
    val iconSize: Dp,
    val textSize: TextUnit,
    val hPadding: Dp,
    val vSpacing: Dp,
    val gridTwoColumns: Boolean
)

@Composable
private fun rememberRoleUiTokens(): RoleUiTokens {
    val cfg = LocalConfiguration.current
    val widthDpInt = maxOf(cfg.screenWidthDp, 320) // без named-аргументов
    val widthClass = rememberAppWidthClass()

    // расчёты через числа -> в dp/sp
    val iconDp = (widthDpInt * 0.18f).coerceIn(40f, 96f)
    val heightDp = (iconDp + 32f).coerceIn(64f, 128f)
    val cornerDp = (iconDp * 0.35f).coerceIn(12f, 28f)
    val textSp = (iconDp * 0.45f).coerceIn(16f, 28f)
    val hPadDp = (widthDpInt * 0.06f).coerceIn(16f, 40f)

    val twoCols = widthClass != AppWidthClass.Compact

    return RoleUiTokens(
        cardHeight = heightDp.dp,
        cardCorner = cornerDp.dp,
        cardBorder = 2.dp,
        iconSize = iconDp.dp,
        textSize = textSp.sp,
        hPadding = hPadDp.dp,
        vSpacing = 24.dp,
        gridTwoColumns = twoCols
    )
}

// ---- Экран выбора роли
@Composable
fun RoleSelectionScreen(navController: NavHostController) {
    val tokens = rememberRoleUiTokens()
    val scheme = MaterialTheme.colorScheme
    val accent = Color(0xFF552216)
    val bg = Color(0xFFF8EDE6)
    val cardBg = Color(0xFFFDE9DD)
    val borderColor = Color(0xFFE0C2BD)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = tokens.hPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tokens.vSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Выбери положение!",
                style = MaterialTheme.typography.headlineLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tokens.gridTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RoleButton(
                        modifier = Modifier.weight(1f),
                        iconId = R.drawable.ic_mommy,   // ← проверь, что такие ресурсы есть
                        text = "Я мамочка!",
                        background = cardBg,
                        border = borderColor,
                        textColor = accent
                    ) { navController.navigate("auth_mommy") }

                    RoleButton(
                        modifier = Modifier.weight(1f),
                        iconId = R.drawable.ic_baby,     // ← и этот тоже
                        text = "Я малыш!!!!",
                        background = cardBg,
                        border = borderColor,
                        textColor = accent
                    ) { navController.navigate("auth_baby") }
                }
            } else {
                RoleButton(
                    iconId = R.drawable.ic_mommy,
                    text = "Я мамочка!",
                    background = cardBg,
                    border = borderColor,
                    textColor = accent
                ) { navController.navigate("auth_mommy") }

                RoleButton(
                    iconId = R.drawable.ic_baby,
                    text = "Я малыш!!!!",
                    background = cardBg,
                    border = borderColor,
                    textColor = accent
                ) { navController.navigate("auth_baby") }
            }
        }
    }
}

// ---- Кнопка роли (не публикуем типы наружу)
@Composable
private fun RoleButton(
    iconId: Int,
    text: String,
    background: Color,
    border: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = rememberRoleUiTokens()
    val shape = RoundedCornerShape(tokens.cardCorner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press-scale")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(tokens.cardBorder, border, shape)
            .clip(shape)
            .background(background)
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .clickable(
                interactionSource = interaction,
                indication = null,   // можно поставить LocalIndication.current для риппла
                onClick = onClick,
                role = Role.Button
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center   // ← центрируем содержимое кнопки
    ) {
        Row(
            modifier = Modifier.wrapContentSize(),   // ← не растягиваемся, чтобы центрировалась связка
            horizontalArrangement = Arrangement.spacedBy(
                (tokens.iconSize * 0.25f).coerceAtMost(20.dp)  // аккуратный зазор
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = null,
                modifier = Modifier.size(tokens.iconSize)
            )
            Text(
                text = text,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = tokens.textSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}