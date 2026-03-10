# Changelog

## 1.0.0 - 2026-03-21

- rebuilt the Android panic client on Jetpack Compose + Material 3;
- switched mobile auth to `panicSecret` with HMAC payload `METHOD\nPATH\nTIMESTAMP`;
- moved provisioning data to `EncryptedSharedPreferences`;
- added demo/provision/build scripts for `emulator -avd my_avd`;
- prepared release APK build flow for GitHub Releases.
