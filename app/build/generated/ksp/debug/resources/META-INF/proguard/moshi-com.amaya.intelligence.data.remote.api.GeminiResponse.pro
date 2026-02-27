-if class com.amaya.intelligence.data.remote.api.GeminiResponse
-keepnames class com.amaya.intelligence.data.remote.api.GeminiResponse
-if class com.amaya.intelligence.data.remote.api.GeminiResponse
-keep class com.amaya.intelligence.data.remote.api.GeminiResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiResponse
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiResponse {
    public synthetic <init>(java.util.List,com.amaya.intelligence.data.remote.api.GeminiUsageMetadata,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
