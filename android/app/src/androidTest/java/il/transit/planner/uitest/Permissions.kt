package il.transit.planner.uitest

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule

object Permissions {
    /** Location, plus notifications where they are a runtime permission (API 33+). */
    fun grantAll(): GrantPermissionRule {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p += Manifest.permission.POST_NOTIFICATIONS
        return GrantPermissionRule.grant(*p.toTypedArray())
    }
}
