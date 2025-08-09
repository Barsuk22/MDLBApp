package com.yourname.mdlbapp.reactions

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.decode.GifDecoder


@Composable
fun ReactionImage(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val request = ImageRequest.Builder(ctx)
        .data(resId)
        .decoderFactory(GifDecoder.Factory())  // вот это для GIF
        .allowHardware(false)                  // отключаем hardware, чтобы анимация шла по-софт-рендеру
        .build()

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier
    )
}