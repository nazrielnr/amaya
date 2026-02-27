-if class com.amaya.intelligence.data.remote.api.AnthropicResponse
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicResponse
-if class com.amaya.intelligence.data.remote.api.AnthropicResponse
-keep class com.amaya.intelligence.data.remote.api.AnthropicResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
