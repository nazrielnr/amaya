-if class com.amaya.intelligence.data.remote.api.AnthropicError
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicError
-if class com.amaya.intelligence.data.remote.api.AnthropicError
-keep class com.amaya.intelligence.data.remote.api.AnthropicErrorJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicError
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicError
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicError {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
