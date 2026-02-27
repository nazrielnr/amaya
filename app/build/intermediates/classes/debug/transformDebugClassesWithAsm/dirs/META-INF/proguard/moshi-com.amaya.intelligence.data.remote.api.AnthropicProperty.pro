-if class com.amaya.intelligence.data.remote.api.AnthropicProperty
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicProperty
-if class com.amaya.intelligence.data.remote.api.AnthropicProperty
-keep class com.amaya.intelligence.data.remote.api.AnthropicPropertyJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.AnthropicProperty
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.AnthropicProperty
-keepclassmembers class com.amaya.intelligence.data.remote.api.AnthropicProperty {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
