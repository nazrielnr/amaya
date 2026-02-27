-if class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent
-if class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent
-keep class com.amaya.intelligence.data.remote.api.AnthropicStreamEventJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicStreamEvent {
    public synthetic <init>(java.lang.String,java.lang.Integer,com.amaya.intelligence.data.remote.api.AnthropicContentBlock,com.amaya.intelligence.data.remote.api.AnthropicDelta,com.amaya.intelligence.data.remote.api.AnthropicUsage,com.amaya.intelligence.data.remote.api.AnthropicError,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
