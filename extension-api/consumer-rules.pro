# Sources are separate APKs loaded in-process (ADR 0003). They call this API and the Kotlin classes it uses by name,
# so R8 keeps their names in any app that depends on it (ADR 0007, ADR 0013). A suspend function takes a
# kotlin.coroutines.Continuation, so the Kotlin library is kept too, still optimised, as Echo does.
-keep public class ani.saikou.neo.extension.api.** { public protected *; }
-keep,allowoptimization public class kotlin.** { public protected *; }
-keep,allowoptimization public class kotlinx.coroutines.** { public protected *; }
