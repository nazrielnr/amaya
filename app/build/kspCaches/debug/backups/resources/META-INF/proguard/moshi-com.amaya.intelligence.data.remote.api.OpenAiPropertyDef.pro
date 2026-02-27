-if class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef
-if class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef
-keep class com.amaya.intelligence.data.remote.api.OpenAiPropertyDefJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiPropertyDef {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,com.amaya.intelligence.data.remote.api.OpenAiItemsDef,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
