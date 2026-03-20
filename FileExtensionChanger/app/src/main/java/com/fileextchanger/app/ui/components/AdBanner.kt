package com.fileextchanger.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = TEST_BANNER_AD_UNIT_ID
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context: Context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
