-if class com.amaya.intelligence.data.remote.api.GeminiRequest
-keepnames class com.amaya.intelligence.data.remote.api.GeminiRequest
-if class com.amaya.intelligence.data.remote.api.GeminiRequest
-keep class com.amaya.intelligence.data.remote.api.GeminiRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiRequest
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiRequest {
    public synthetic <init>(java.util.List,com.amaya.intelligence.data.remote.api.GeminiContent,java.util.List,com.amaya.intelligence.data.remote.api.GeminiGenerationConfig,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
