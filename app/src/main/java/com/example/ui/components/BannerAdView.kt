
package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AdsManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Displays only the banner ad when it successfully loads.
 * No decorative container, AD label, or Sponsored label.
 */
@Composable
fun AppBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsManager.getBannerAdUnitId(),
    showBorderCard: Boolean = true
) {
    val isPreview = LocalInspectionMode.current
    var isAdLoaded by remember { mutableStateOf(false) }
    var adViewRef by remember { mutableStateOf<AdView?>(null) }

    if (isPreview) {
        // Keep previews clean without displaying an ad placeholder.
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("app_banner_ad_container")
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { context ->
                // Prefer the banner requested at app start. If it is not ready yet,
                // make a normal request rather than delaying the current screen.
                val preloadedAd = AdsManager.takePreloadedBannerAd()
                (preloadedAd ?: AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                }).apply {

                    // Keep the view in the layout while its first request is in flight.
                    // GONE prevents Android from measuring/layouting the AdView at all;
                    // INVISIBLE avoids showing an empty banner while still giving the
                    // SDK an attached, measured view for the initial request.
                    visibility = android.view.View.INVISIBLE

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                            visibility = android.view.View.VISIBLE
                            requestLayout()
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isAdLoaded = false
                            visibility = android.view.View.GONE
                            requestLayout()
                        }
                    }

                    adViewRef = this

                    if (preloadedAd != null) {
                        isAdLoaded = true
                        visibility = android.view.View.VISIBLE
                    } else {
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (_: Exception) {
                            isAdLoaded = false
                            visibility = android.view.View.GONE
                        }
                    }
                }
            },
            update = { adView ->
                adView.visibility = if (isAdLoaded) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            },
            onRelease = { adView ->
                try {
                    adView.destroy()
                } catch (_: Exception) {
                    // Ignore cleanup errors.
                }

                if (adViewRef === adView) {
                    adViewRef = null
                }

                // Have the following screen's banner load while the user moves
                // through the current screen instead of starting at navigation.
                AdsManager.preloadBannerAd(adView.context.applicationContext)
            }
        )
    }
}
