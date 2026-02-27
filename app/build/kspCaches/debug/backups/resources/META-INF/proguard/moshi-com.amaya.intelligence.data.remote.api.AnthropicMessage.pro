-if class com.amaya.intelligence.data.remote.api.AnthropicMessage
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicMessage
-if class com.amaya.intelligence.data.remote.api.AnthropicMessage
-keep class com.amaya.intelligence.data.remote.api.AnthropicMessageJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
