# Keep rules for Video Text Translator
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }
-keepattributes *Annotation*,InnerClasses
-dontwarn org.vosk.**
-dontwarn com.googlecode.tesseract.**
