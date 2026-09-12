# kotlinx.serialization garde les serializers generes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.mkdev.prowlarrexplorer.** {
    *** Companion;
}
-keepclasseswithmembers class dev.mkdev.prowlarrexplorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
