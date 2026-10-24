# Extensions are separate APKs loaded in-process (ADR 0003). They call the
# API by its real names, so R8 must not rename or remove any of it.
-keep class ani.saikou.neo.extension.api.** { public protected *; }

# Extensions also share the app's Kotlin classes: the API's suspend functions
# take a kotlin.coroutines.Continuation, for one. As in Echo, the public Kotlin
# and coroutines API keeps its names, though R8 may still optimise the code.
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
