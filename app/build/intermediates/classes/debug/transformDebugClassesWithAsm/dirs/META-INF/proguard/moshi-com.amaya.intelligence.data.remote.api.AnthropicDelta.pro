-if class com.amaya.intelligence.data.remote.api.AnthropicDelta
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicDelta
-if class com.amaya.intelligence.data.remote.api.AnthropicDelta
-keep class com.amaya.intelligence.data.remote.api.AnthropicDeltaJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicDelta
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicDelta
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicDelta {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
