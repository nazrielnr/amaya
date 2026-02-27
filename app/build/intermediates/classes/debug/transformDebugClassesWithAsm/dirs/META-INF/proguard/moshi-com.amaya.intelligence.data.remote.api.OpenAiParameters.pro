-if class com.amaya.intelligence.data.remote.api.OpenAiParameters
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiParameters
-if class com.amaya.intelligence.data.remote.api.OpenAiParameters
-keep class com.amaya.intelligence.data.remote.api.OpenAiParametersJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiParameters
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiParameters
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiParameters {
    public synthetic <init>(java.lang.String,java.util.Map,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
