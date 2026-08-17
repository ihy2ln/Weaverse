# Add project specific ProGuard rules here.
-keep class com.ihy2ln.weaverse.** { *; }
-dontwarn org.slf4j.**

# Ktor's IntellijIdeaDebugDetector optionally probes java.lang.management (desktop-JVM-only,
# absent on Android ART) to detect a connected debugger; sqlite-jdbc's JDBC3PreparedStatement
# optionally references java.sql.JDBCType. Both are graceful-degradation code paths never hit
# on Android — R8 full-mode shrinking treats the missing classes as hard errors unless told
# they're expected to be absent at runtime.
-dontwarn java.lang.management.**
-dontwarn java.sql.**
-dontwarn org.sqlite.**
