-if class com.amaya.intelligence.data.remote.api.GeminiCandidate
-keepnames class com.amaya.intelligence.data.remote.api.GeminiCandidate
-if class com.amaya.intelligence.data.remote.api.GeminiCandidate
-keep class com.amaya.intelligence.data.remote.api.GeminiCandidateJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.amaya.intelligence.data.remote.api.GeminiCandidate
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.amaya.intelligence.data.remote.api.GeminiCandidate
-keepclassmembers class com.amaya.intelligence.data.remote.api.GeminiCandidate {
    public synthetic <init>(com.amaya.intelligence.data.remote.api.GeminiContent,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
