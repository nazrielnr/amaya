-if class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig
-keepnames class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig
-if class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig
-keep class com.amaya.intelligence.data.remote.api.GeminiGenerationConfigJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiGenerationConfig {
    public synthetic <init>(int,float,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
