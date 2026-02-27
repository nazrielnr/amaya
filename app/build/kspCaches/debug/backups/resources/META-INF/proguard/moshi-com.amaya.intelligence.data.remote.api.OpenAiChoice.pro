-if class com.amaya.intelligence.data.remote.api.OpenAiChoice
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiChoice
-if class com.amaya.intelligence.data.remote.api.OpenAiChoice
-keep class com.amaya.intelligence.data.remote.api.OpenAiChoiceJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
