-if class com.amaya.intelligence.data.remote.api.OpenAiToolCall
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiToolCall
-if class com.amaya.intelligence.data.remote.api.OpenAiToolCall
-keep class com.amaya.intelligence.data.remote.api.OpenAiToolCallJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiToolCall
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiToolCall
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiToolCall {
    public synthetic <init>(java.lang.String,java.lang.String,com.amaya.intelligence.data.remote.api.OpenAiFunction,java.lang.Integer,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
