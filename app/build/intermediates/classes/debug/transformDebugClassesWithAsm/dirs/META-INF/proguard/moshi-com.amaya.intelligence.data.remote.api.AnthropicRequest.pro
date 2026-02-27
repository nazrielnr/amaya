-if class com.amaya.intelligence.data.remote.api.AnthropicRequest
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicRequest
-if class com.amaya.intelligence.data.remote.api.AnthropicRequest
-keep class com.amaya.intelligence.data.remote.api.AnthropicRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicRequest
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicRequest {
    public synthetic <init>(java.lang.String,java.util.List,java.lang.String,java.util.List,int,boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
