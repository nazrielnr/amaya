-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall
-keep class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCallJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiDeltaToolCall {
    public synthetic <init>(java.lang.Integer,java.lang.String,java.lang.String,com.amaya.intelligence.data.remote.api.OpenAiDeltaFunction,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
