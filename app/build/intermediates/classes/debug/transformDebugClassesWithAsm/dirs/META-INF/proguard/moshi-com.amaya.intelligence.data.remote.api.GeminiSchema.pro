-if class com.amaya.intelligence.data.remote.api.GeminiSchema
-keepnames class com.amaya.intelligence.data.remote.api.GeminiSchema
-if class com.amaya.intelligence.data.remote.api.GeminiSchema
-keep class com.amaya.intelligence.data.remote.api.GeminiSchemaJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiSchema
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiSchema
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiSchema {
    public synthetic <init>(java.lang.String,java.util.Map,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
