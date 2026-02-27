-if class com.amaya.intelligence.data.remote.api.AnthropicContentBlock
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicContentBlock
-if class com.amaya.intelligence.data.remote.api.AnthropicContentBlock
-keep class com.amaya.intelligence.data.remote.api.AnthropicContentBlockJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicContentBlock
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicContentBlock
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicContentBlock {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.util.Map,java.lang.String,java.lang.String,java.lang.Boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
