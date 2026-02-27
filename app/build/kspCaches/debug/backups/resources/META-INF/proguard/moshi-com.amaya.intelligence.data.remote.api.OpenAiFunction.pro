-if class com.amaya.intelligence.data.remote.api.OpenAiFunction
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiFunction
-if class com.amaya.intelligence.data.remote.api.OpenAiFunction
-keep class com.amaya.intelligence.data.remote.api.OpenAiFunctionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
