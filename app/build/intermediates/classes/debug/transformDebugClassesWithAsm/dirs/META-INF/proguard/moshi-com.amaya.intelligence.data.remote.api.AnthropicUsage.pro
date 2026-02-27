-if class com.amaya.intelligence.data.remote.api.AnthropicUsage
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicUsage
-if class com.amaya.intelligence.data.remote.api.AnthropicUsage
-keep class com.amaya.intelligence.data.remote.api.AnthropicUsageJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
