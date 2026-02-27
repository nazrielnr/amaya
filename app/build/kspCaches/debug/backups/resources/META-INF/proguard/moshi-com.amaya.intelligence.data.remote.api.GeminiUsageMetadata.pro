-if class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata
-keepnames class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata
-if class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata
-keep class com.amaya.intelligence.data.remote.api.GeminiUsageMetadataJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiUsageMetadata {
    public synthetic <init>(int,int,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
