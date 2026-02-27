-if class com.amaya.intelligence.data.remote.api.OpenAiFunctionDef
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiFunctionDef
-if class com.amaya.intelligence.data.remote.api.OpenAiFunctionDef
-keep class com.amaya.intelligence.data.remote.api.OpenAiFunctionDefJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
