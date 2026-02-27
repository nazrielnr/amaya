-if class com.amaya.intelligence.data.remote.api.OpenAiRequest
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiRequest
-if class com.amaya.intelligence.data.remote.api.OpenAiRequest
-keep class com.amaya.intelligence.data.remote.api.OpenAiRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiRequest
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiRequest {
    public synthetic <init>(java.lang.String,java.util.List,java.util.List,int,float,boolean,com.amaya.intelligence.data.remote.api.OpenAiStreamOptions,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
