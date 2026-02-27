-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction
-keep class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunctionJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
