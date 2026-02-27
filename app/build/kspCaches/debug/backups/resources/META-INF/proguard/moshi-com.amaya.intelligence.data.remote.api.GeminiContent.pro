-if class com.amaya.intelligence.data.remote.api.GeminiContent
-keepnames class com.amaya.intelligence.data.remote.api.GeminiContent
-if class com.amaya.intelligence.data.remote.api.GeminiContent
-keep class com.amaya.intelligence.data.remote.api.GeminiContentJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiContent
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiContent
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiContent {
    public synthetic <init>(java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
