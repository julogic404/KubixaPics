# Regras de consumo do core:data.

# avif-coder usa JNI: nomes de classes e metodos nativos nao podem ser alterados.
-keep class com.radzivon.bartoshyk.avif.coder.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
