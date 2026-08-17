# kotlinx.serialization keeps its own consumer rules, but the serializer()
# lookup for our sealed Block/Span hierarchy is reflective, so pin it here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclasseswithmembers class com.ihy2ln.weaverse.**.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.ihy2ln.weaverse.** implements kotlinx.serialization.KSerializer {
    <fields>;
}
-keep,includedescriptorclasses class com.ihy2ln.weaverse.**$$serializer { *; }

# Room entities are constructed by generated code via reflection in some paths.
-keep class com.ihy2ln.weaverse.data.db.entity.** { *; }

# OkHttp (via Ktor's OkHttp engine) does an optional reflective lookup for an
# slf4j logging binding at runtime, falling back to a no-op logger if one
# isn't on the classpath -- which it isn't here. R8 treats the missing class
# as an error under full-mode shrinking unless told it's fine to not know
# about it; first surfaced when `assembleRelease` ran for the first time
# ever in CI (release.yml, tagging v0.1.0) since build.yml only exercises
# assembleDebug, which never runs R8.
-dontwarn org.slf4j.**
