-if class com.amaya.intelligence.data.remote.api.GeminiPart
-keepnames class com.amaya.intelligence.data.remote.api.GeminiPart
-if class com.amaya.intelligence.data.remote.api.GeminiPart
-keep class com.amaya.intelligence.data.remote.api.GeminiPartJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiPart
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiPart
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiPart {
    public synthetic <init>(java.lang.String,com.amaya.intelligence.data.remote.api.GeminiFunctionCall,com.amaya.intelligence.data.remote.api.GeminiFunctionResponse,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
