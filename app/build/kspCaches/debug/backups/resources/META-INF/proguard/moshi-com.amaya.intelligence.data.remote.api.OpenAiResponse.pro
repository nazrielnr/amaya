-if class com.amaya.intelligence.data.remote.api.OpenAiResponse
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiResponse
-if class com.amaya.intelligence.data.remote.api.OpenAiResponse
-keep class com.amaya.intelligence.data.remote.api.OpenAiResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
