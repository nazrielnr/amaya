-if class com.amaya.intelligence.data.remote.api.OpenAiToolDef
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiToolDef
-if class com.amaya.intelligence.data.remote.api.OpenAiToolDef
-keep class com.amaya.intelligence.data.remote.api.OpenAiToolDefJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiToolDef
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiToolDef
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiToolDef {
    public synthetic <init>(java.lang.String,com.amaya.intelligence.data.remote.api.OpenAiFunctionDef,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
