-if class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions
-keep class com.amaya.intelligence.data.remote.api.OpenAiStreamOptionsJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiStreamOptions {
    public synthetic <init>(boolean,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
