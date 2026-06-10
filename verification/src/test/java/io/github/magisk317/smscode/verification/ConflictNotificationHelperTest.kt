package io.github.magisk317.smscode.verification

import android.app.PendingIntent
import android.os.Build
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConflictNotificationHelperTest {
    @Test
    fun activityPendingIntentFlags_usesConfiguredImmutableThreshold() {
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT,
            ConflictNotificationHelper.activityPendingIntentFlags(
                immutableMinSdk = Build.VERSION_CODES.S,
                sdkInt = Build.VERSION_CODES.R,
            ),
        )
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ConflictNotificationHelper.activityPendingIntentFlags(
                immutableMinSdk = Build.VERSION_CODES.M,
                sdkInt = Build.VERSION_CODES.R,
            ),
        )
    }
}
