-if class com.amaya.intelligence.data.remote.api.AnthropicTool
-keepnames class com.amaya.intelligence.data.remote.api.AnthropicTool
-if class com.amaya.intelligence.data.remote.api.AnthropicTool
-keep class com.amaya.intelligence.data.remote.api.AnthropicToolJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
