-if class com.amaya.intelligence.data.remote.api.OpenAiItemsDef
-keepnames class com.amaya.intelligence.data.remote.api.OpenAiItemsDef
-if class com.amaya.intelligence.data.remote.api.OpenAiItemsDef
-keep class com.amaya.intelligence.data.remote.api.OpenAiItemsDefJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.OpenAiItemsDef
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.OpenAiItemsDef
-keepclassmembers class com.amaya.intelligence.data.remote.api.OpenAiItemsDef {
    public synthetic <init>(java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
