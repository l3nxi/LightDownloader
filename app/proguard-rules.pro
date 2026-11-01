# youtubedl-android uses reflection/Jackson to map yt-dlp JSON.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties <fields>;
}
-dontwarn org.apache.commons.**
-dontwarn com.fasterxml.jackson.**

# Keep the Python bridge and its JNI entry points.
-keep class com.yausername.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep annotations used by Jackson and Room.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn org.apache.commons.**
-dontwarn com.fasterxml.jackson.**
