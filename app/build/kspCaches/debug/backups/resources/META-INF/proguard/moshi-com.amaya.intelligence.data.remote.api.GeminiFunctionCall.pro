-if class com.amaya.intelligence.data.remote.api.GeminiFunctionCall
-keepnames class com.amaya.intelligence.data.remote.api.GeminiFunctionCall
-if class com.amaya.intelligence.data.remote.api.GeminiFunctionCall
-keep class com.amaya.intelligence.data.remote.api.GeminiFunctionCallJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiFunctionCall
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiFunctionCall
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiFunctionCall {
    public synthetic <init>(java.lang.String,java.util.Map,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
