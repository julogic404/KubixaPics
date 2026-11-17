# Regras ProGuard/R8 do modulo de aplicacao (Kubixa Pics).
#
# As regras especificas de cada modulo vivem nos respetivos consumer-rules.pro
# e sao aplicadas automaticamente por quem depende deles. Este ficheiro cobre
# apenas o que e do app: ViewBinding, modelos e atributos de diagnostico.

# ViewBinding do app
-keep class ao.julogic404.kubixapics.databinding.** { *; }

# Modelos partilhados (usados em comparacoes e possivel serializacao futura)
-keep class ao.julogic404.kubixapics.core.common.model.** { *; }

# Activities referenciadas pelo AndroidManifest
-keep class ao.julogic404.kubixapics.PrincipalAtiv { *; }
-keep class ao.julogic404.kubixapics.SobreAtiv { *; }
-keep class ao.julogic404.kubixapics.feature.convert.ConverterAtiv { *; }
-keep class ao.julogic404.kubixapics.feature.resize.RedimensionarAtiv { *; }
-keep class ao.julogic404.kubixapics.feature.crop.CortarAtiv { *; }
-keep class ao.julogic404.kubixapics.feature.compress.ComprimirAtiv { *; }

# ViewModels instanciados por reflexao pelo AndroidX
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# Manter nomes de linha para relatorios de crash legiveis
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remover avisos de dependencias opcionais
-dontwarn org.jetbrains.annotations.**
