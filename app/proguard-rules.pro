# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google Play Billing — the library ships its own consumer rules but we
# add these as a safety net for any reflection-based access.
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }

# Kotlin coroutines (used by BillingManager)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose — keep lambdas and inline functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
