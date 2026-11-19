# Copyright 2026 JuLogic 404
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
-keep class ao.julogic404.kubixapics.AberturaAtiv { *; }
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

# ---------------------------------------------------------------------------
# Remocao de logs no release (so funciona com proguard-android-optimize.txt).
# Evita expor informacao interna em `adb logcat`. As chamadas Log.* desaparecem
# do bytecode final (incluindo as de bibliotecas incluidas no app).
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
