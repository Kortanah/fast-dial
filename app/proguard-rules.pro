# FlowDial ProGuard / R8 Code Obfuscation & Encapsulation Rules

# Preserve line numbers and source file names for crash reporting while obfuscating class/method names
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep Moshi data models / JSON serialization models intact from field renaming
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.engine.EngineModels** { *; }
-keep class com.example.gate.GateModels** { *; }

# Keep Room database entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Optimize and aggressively obfuscate internal business logic
-repackageclasses 'a'
-allowaccessmodification

# Keep Compose view models and entry points
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

