# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.switchpulse.app.**$$serializer { *; }
-keepclassmembers class com.switchpulse.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.switchpulse.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance / WorkManager reflection safety
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
