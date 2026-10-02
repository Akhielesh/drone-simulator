# Datum release shrinking rules. Libraries (ARCore, CameraX, DataStore, Compose) ship their own
# consumer rules; these cover the app's own reflection-sensitive code.

# Saved measurements, objects and drafts are persisted as JSON with kotlinx.serialization. Keep the
# models and their generated serializers intact so data written by one build reads back in the next.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep class com.akhielesh.datum.core.data.** { *; }
-keepclassmembers class com.akhielesh.datum.** {
    *** Companion;
}
-keepclasseswithmembers class com.akhielesh.datum.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.akhielesh.datum.**$$serializer { *; }

# Readable stack traces in crash reports without shipping full names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
