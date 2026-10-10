
package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

object AdsManager {

    private const val TAG = "AdsManager"

    private val isInitialized = AtomicBoolean(false)

    // Production AdMob ad-unit IDs
    const val PROD_BANNER_AD_UNIT_ID =
        "ca-app-pub-6422662628736314/2257767672"

    const val PROD_INTERSTITIAL_AD_UNIT_ID =
        "ca-app-pub-6422662628736314/9347125481"

    const val PROD_REWARDED_AD_UNIT_ID =
        "ca-app-pub-6422662628736314/7986028075"

    // Google's official sample ad-unit IDs for testing
    const val SAMPLE_BANNER_AD_UNIT_ID =
        "ca-app-pub-3940256099942544/6300978111"

    const val SAMPLE_INTERSTITIAL_AD_UNIT_ID =
        "ca-app-pub-3940256099942544/1033173712"

    const val SAMPLE_REWARDED_AD_UNIT_ID =
        "ca-app-pub-3940256099942544/5224354917"

    // Debug builds use sample ads; release builds use production ads.
    fun getBannerAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            SAMPLE_BANNER_AD_UNIT_ID
        } else {
            PROD_BANNER_AD_UNIT_ID
        }
    }

    fun getInterstitialAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            SAMPLE_INTERSTITIAL_AD_UNIT_ID
        } else {
            PROD_INTERSTITIAL_AD_UNIT_ID
        }
    }

    fun getRewardedAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            SAMPLE_REWARDED_AD_UNIT_ID
        } else {
            PROD_REWARDED_AD_UNIT_ID
        }
    }

    @Volatile
    private var preloadedRewardedAd: RewardedAd? = null

    @Volatile
    private var isRewardedLoading = false

    @Volatile
    private var preloadedInterstitialAd: InterstitialAd? = null

    @Volatile
    private var isInterstitialLoading = false

    @Volatile
    private var preloadedBannerAd: AdView? = null

    @Volatile
    private var isBannerLoading = false

    /**
     * Initializes the Mobile Ads SDK once per app process.
     */
    fun initialize(context: Context) {
        if (!isInitialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext

        try {
            Thread {
                try {
                    val requestConfig = RequestConfiguration.Builder()
                        .setTagForChildDirectedTreatment(
                            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                        )
                        .build()

                    MobileAds.setRequestConfiguration(requestConfig)

                    MobileAds.initialize(appContext) { status ->
                        Log.d(
                            TAG,
                            "AdMob SDK initialized: ${status.adapterStatusMap}"
                        )

                        preloadBannerAd(appContext)
                        preloadRewardedAd(appContext)
                        preloadInterstitialAd(appContext)
                    }
                } catch (e: Exception) {
                    isInitialized.set(false)
                    Log.e(TAG, "AdMob initialization failed", e)
                }
            }.start()
        } catch (e: Exception) {
            isInitialized.set(false)
            Log.e(TAG, "Could not start AdMob initialization", e)
        }
    }

    /**
     * Starts the next banner request before a screen needs to show it.
     * A banner is handed to one screen only; that screen destroys it when it
     * leaves and immediately starts preloading the next banner.
     */
    @Synchronized
    fun preloadBannerAd(context: Context) {
        if (preloadedBannerAd != null || isBannerLoading) return

        isBannerLoading = true
        val appContext = context.applicationContext

        Handler(Looper.getMainLooper()).post {
            synchronized(this@AdsManager) {
                if (preloadedBannerAd != null || !isBannerLoading) return@post
            }

            try {
                AdView(appContext).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = getBannerAdUnitId()
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            synchronized(this@AdsManager) {
                                preloadedBannerAd = this@apply
                                isBannerLoading = false
                            }
                            Log.d(TAG, "Banner ad preloaded successfully")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            synchronized(this@AdsManager) {
                                isBannerLoading = false
                            }
                            destroy()
                            Log.w(TAG, "Banner preload failed: ${loadAdError.message}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            } catch (e: Exception) {
                synchronized(this@AdsManager) {
                    isBannerLoading = false
                }
                Log.e(TAG, "Could not preload banner ad", e)
            }
        }
    }

    /** Returns a loaded banner once, or null when the preload is not ready. */
    @Synchronized
    fun takePreloadedBannerAd(): AdView? {
        return preloadedBannerAd.also { preloadedBannerAd = null }
    }

    /**
     * Preloads an interstitial ad for later display.
     */
    @Synchronized
    fun preloadInterstitialAd(context: Context) {
        if (preloadedInterstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true

        val appContext = context.applicationContext
        val adRequest = AdRequest.Builder().build()

        try {
            InterstitialAd.load(
                appContext,
                getInterstitialAdUnitId(),
                adRequest,
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        preloadedInterstitialAd = interstitialAd
                        isInterstitialLoading = false

                        Log.d(TAG, "Interstitial ad preloaded successfully")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        preloadedInterstitialAd = null
                        isInterstitialLoading = false

                        Log.w(
                            TAG,
                            "Interstitial preload failed: ${loadAdError.message}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.e(TAG, "Could not request interstitial ad", e)
        }
    }

    /**
     * Shows an interstitial ad before an action such as opening Settings.
     * Calls onAdDismissed when the ad closes or cannot be shown.
     */
    fun showInterstitialAd(
        context: Context,
        onAdDismissed: () -> Unit
    ) {
        val activity = context.findActivity()

        if (activity == null) {
            Log.w(TAG, "No Activity available for interstitial ad")
            onAdDismissed()
            return
        }

        val adToShow = synchronized(this) {
            preloadedInterstitialAd.also {
                preloadedInterstitialAd = null
            }
        }

        if (adToShow != null) {
            adToShow.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        preloadInterstitialAd(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        adError: AdError
                    ) {
                        Log.w(
                            TAG,
                            "Interstitial failed to show: ${adError.message}"
                        )
                        preloadInterstitialAd(activity.applicationContext)
                        onAdDismissed()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad displayed")
                    }
                }

            try {
                adToShow.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Could not display interstitial ad", e)
                preloadInterstitialAd(activity.applicationContext)
                onAdDismissed()
            }

            return
        }

        // No preloaded ad: try loading one immediately.
        Log.d(TAG, "Loading interstitial on demand")

        try {
            InterstitialAd.load(
                activity,
                getInterstitialAdUnitId(),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        interstitialAd.fullScreenContentCallback =
                            object : FullScreenContentCallback() {

                                override fun onAdDismissedFullScreenContent() {
                                    preloadInterstitialAd(
                                        activity.applicationContext
                                    )
                                    onAdDismissed()
                                }

                                override fun onAdFailedToShowFullScreenContent(
                                    adError: AdError
                                ) {
                                    Log.w(
                                        TAG,
                                        "Interstitial failed to show: ${adError.message}"
                                    )
                                    preloadInterstitialAd(
                                        activity.applicationContext
                                    )
                                    onAdDismissed()
                                }
                            }

                        try {
                            interstitialAd.show(activity)
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not display interstitial ad", e)
                            preloadInterstitialAd(activity.applicationContext)
                            onAdDismissed()
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(
                            TAG,
                            "On-demand interstitial load failed: ${loadAdError.message}"
                        )
                        onAdDismissed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not load interstitial ad", e)
            onAdDismissed()
        }
    }

    /**
     * Preloads a rewarded ad.
     */
    @Synchronized
    fun preloadRewardedAd(context: Context) {
        if (preloadedRewardedAd != null || isRewardedLoading) return

        isRewardedLoading = true

        val appContext = context.applicationContext

        try {
            RewardedAd.load(
                appContext,
                getRewardedAdUnitId(),
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {

                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        preloadedRewardedAd = rewardedAd
                        isRewardedLoading = false

                        Log.d(TAG, "Rewarded ad preloaded successfully")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        preloadedRewardedAd = null
                        isRewardedLoading = false

                        Log.w(
                            TAG,
                            "Rewarded preload failed: ${loadAdError.message}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            isRewardedLoading = false
            Log.e(TAG, "Could not request rewarded ad", e)
        }
    }

    /**
     * Shows a rewarded ad.
     *
     * rewardEarned is true only when the SDK reports that the user earned
     * the reward. If the ad cannot be loaded or shown, this implementation
     * preserves the existing behavior of allowing the app action to continue.
     */
    fun showRewardedAd(
        context: Context,
        onAdDismissedOrCompleted: (rewardEarned: Boolean) -> Unit
    ) {
        val activity = context.findActivity()

        if (activity == null) {
            Log.w(TAG, "No Activity available for rewarded ad")
            onAdDismissedOrCompleted(true)
            return
        }

        val adToShow = synchronized(this) {
            preloadedRewardedAd.also {
                preloadedRewardedAd = null
            }
        }

        if (adToShow != null) {
            showLoadedRewardedAd(
                activity = activity,
                rewardedAd = adToShow,
                onAdDismissedOrCompleted = onAdDismissedOrCompleted
            )
            return
        }

        // No preloaded ad: load one on demand.
        Log.d(TAG, "Loading rewarded ad on demand")

        try {
            RewardedAd.load(
                activity,
                getRewardedAdUnitId(),
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {

                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        showLoadedRewardedAd(
                            activity = activity,
                            rewardedAd = rewardedAd,
                            onAdDismissedOrCompleted = onAdDismissedOrCompleted
                        )
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(
                            TAG,
                            "On-demand rewarded load failed: ${loadAdError.message}"
                        )

                        // Preserves the original fallback behavior.
                        onAdDismissedOrCompleted(true)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not load rewarded ad", e)
            onAdDismissedOrCompleted(true)
        }
    }

    private fun showLoadedRewardedAd(
        activity: Activity,
        rewardedAd: RewardedAd,
        onAdDismissedOrCompleted: (Boolean) -> Unit
    ) {
        var userEarnedReward = false

        rewardedAd.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    Log.d(
                        TAG,
                        "Rewarded ad dismissed; earned=$userEarnedReward"
                    )

                    preloadRewardedAd(activity.applicationContext)
                    onAdDismissedOrCompleted(userEarnedReward)
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: AdError
                ) {
                    Log.w(
                        TAG,
                        "Rewarded ad failed to show: ${adError.message}"
                    )

                    preloadRewardedAd(activity.applicationContext)

                    // Preserves the original fallback behavior.
                    onAdDismissedOrCompleted(true)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad displayed")
                }
            }

        try {
            rewardedAd.show(activity) { rewardItem ->
                userEarnedReward = true

                Log.d(
                    TAG,
                    "User earned reward: ${rewardItem.amount} ${rewardItem.type}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not display rewarded ad", e)
            preloadRewardedAd(activity.applicationContext)

            // Preserves the original fallback behavior.
            onAdDismissedOrCompleted(true)
        }
    }
}

/**
 * Finds the Activity wrapped by a Context, if one exists.
 */
fun Context.findActivity(): Activity? {
    var currentContext: Context = this

    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }

        val baseContext = currentContext.baseContext

        if (baseContext === currentContext) {
            return null
        }

        currentContext = baseContext
    }

    return null
}
