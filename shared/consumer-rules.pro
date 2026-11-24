# The app log names the exception that failed (AppLog), so R8 keeps the names of exception classes.
-keepnames class * extends java.lang.Throwable
