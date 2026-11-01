package il.transit.core.present

/**
 * A payment app the trip sheet can open (Phase 11 E4). We never take a payment: the button opens
 * the other app, or its store page when it is not installed. [checkedOn] is the day its package
 * name was confirmed against its Google Play listing.
 */
data class PayApp(
    val packageName: String,
    val nameEn: String,
    val nameHe: String,
    val checkedOn: String,
) {
    fun name(language: String): String = if (language.startsWith("he") || language.startsWith("iw")) nameHe else nameEn

    /** The Play Store app's own link; [webStoreUrl] when no store app answers it. */
    val marketUri: String get() = "market://details?id=$packageName"
    val webStoreUrl: String get() = "https://play.google.com/store/apps/details?id=$packageName"
}

object PayApps {
    /**
     * The owner's list (Phase 11 owner decision 1), in that order. Package names confirmed from
     * each app's Google Play listing on 2026-10-31. "ANY WAY by Isracard" (`com.isracard.payments`)
     * is left out: its listing says the app ended its service in 2022.
     */
    val ALL: List<PayApp> = listOf(
        PayApp("com.pcentra.ravkavonlinemobile", "Rav-Kav Online", "רב-קו אונליין", "2026-10-31"),
        PayApp("co.hopon.client", "Rav-Pass by HopOn", "הופאון רב-פס", "2026-10-31"),
        PayApp("com.tranzmate", "Moovit", "מוביט", "2026-10-31"),
    )

    fun byPackage(packageName: String?): PayApp? = packageName?.let { p -> ALL.firstOrNull { it.packageName == p } }

    /** The chooser's order: installed apps first, each group in the owner's order. */
    fun chooserOrder(installed: Set<String>): List<PayApp> = ALL.sortedBy { it.packageName !in installed }

    /**
     * The user's own choice ([il.transit.core.user.UserSettings.paymentApp]) if it is still on the
     * list; null = "Pay with…" opens the chooser.
     */
    fun chosen(paymentApp: String?): PayApp? = byPackage(paymentApp)
}
