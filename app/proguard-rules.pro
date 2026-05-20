# Keep data model classes for JSON serialisation
-keep class dev.hwrecon.model.** { *; }
-keep class dev.hwrecon.export.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
