-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChoice
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiStreamChoice
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChoice
-keep class com.amaya.intelligence.data.remote.api.OpenAiStreamChoiceJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
