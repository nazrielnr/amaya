-if class com.amaya.intelligence.tools.FileInfo
-keepnames class com.amaya.intelligence.tools.FileInfo
-if class com.amaya.intelligence.tools.FileInfo
-keep class com.amaya.intelligence.tools.FileInfoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
