# Disposable TLS test identity

The TLS regression tests generate a short-lived localhost certificate and disposable private key with the JDK keytool. The temporary keystore is loaded in memory and deleted immediately. No test private key is committed, uploaded or packaged in the APK.
