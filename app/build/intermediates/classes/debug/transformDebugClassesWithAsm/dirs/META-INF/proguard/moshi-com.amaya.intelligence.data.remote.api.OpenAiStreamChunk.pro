-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk
-keep class com.amaya.intelligence.data.remote.api.OpenAiStreamChunkJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiStreamChunk {
    public synthetic <init>(java.lang.String,java.lang.String,long,java.lang.String,java.util.List,com.amaya.intelligence.data.remote.api.OpenAiUsage,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
