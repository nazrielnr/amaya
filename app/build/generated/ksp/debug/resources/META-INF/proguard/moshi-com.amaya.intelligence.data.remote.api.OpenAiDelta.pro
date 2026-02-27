-if class com.amaya.intelligence.data.remote.api.OpenAiDelta
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiDelta
-if class com.amaya.intelligence.data.remote.api.OpenAiDelta
-keep class com.amaya.intelligence.data.remote.api.OpenAiDeltaJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiDelta
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiDelta
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiDelta {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
