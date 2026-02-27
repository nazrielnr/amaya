-if class com.amaya.intelligence.data.remote.api.OpenAiMessage
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiMessage
-if class com.amaya.intelligence.data.remote.api.OpenAiMessage
-keep class com.amaya.intelligence.data.remote.api.OpenAiMessageJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiMessage
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiMessage
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiMessage {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
