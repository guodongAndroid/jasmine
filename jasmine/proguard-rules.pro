# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 设定优化次数
-optimizationpasses 5

# 包名不混合大小写
-dontusemixedcaseclassnames

# 不去忽略非公共的库类
-dontskipnonpubliclibraryclasses

# 指定不去忽略非公共库的成员
-dontskipnonpubliclibraryclassmembers

# 优化 不优化输入的类文件
-dontoptimize

# 预校验
-dontpreverify

# 混淆时是否记录日志
-verbose

# 混淆时所采用的算法
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# 保护注解
-keepattributes *Annotation*,InnerClasses

-keepattributes Exceptions

# 避免混淆泛型
-keepattributes Signature

# 抛出异常时保留代码行号
-keepattributes SourceFile,LineNumberTable

# 不要删除无用代码
-dontshrink

-allowaccessmodification

# 不混淆枚举中的values()和valueOf()方法
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn java.lang.invoke.StringConcatFactory

-repackageclasses com.guodong.android.jasmine

# netty
-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-keep class io.netty.handler.codec.** { *; }
-keep class io.netty.buffer.** { *; }
-keep class io.netty.channel.** { *; }
-keep class io.netty.util.** { *; }
-keep class io.netty.internal.** { *; }

-keepclassmembernames class io.netty.** {
    *;
}

-keep public class * extends io.netty.handler.codec.DecoderException
-keep public class * extends io.netty.handler.codec.EncoderException