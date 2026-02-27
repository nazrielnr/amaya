-if class com.amaya.intelligence.data.remote.api.GeminiFunctionResponse
-keepnames class com.amaya.intelligence.data.remote.api.GeminiFunctionResponse
-if class com.amaya.intelligence.data.remote.api.GeminiFunctionResponse
-keep class com.amaya.intelligence.data.remote.api.GeminiFunctionResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
