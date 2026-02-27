-if class com.amaya.intelligence.data.remote.api.OpenAiUsage
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiUsage
-if class com.amaya.intelligence.data.remote.api.OpenAiUsage
-keep class com.amaya.intelligence.data.remote.api.OpenAiUsageJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
