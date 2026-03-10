# CorePost Mobile Android

Android panic-клиент для CorePost на Jetpack Compose и Material 3. Приложение хранит `baseUrl`, `emergencyId` и `panicSecret` в `EncryptedSharedPreferences`, поддерживает двухшаговый panic-flow и синхронизирует состояние устройства через HMAC-подписанные mobile-запросы.

## Конфиг

Адрес сервера не зашит в приложение. Пользователь вводит его на onboarding-экране и может позже:

- изменить адрес;
- заменить `emergencyId` и `panicSecret`;
- полностью удалить все сохраненные параметры.

`debug`-сборка допускает `http://` для локальной демонстрации. `release`-сборка по умолчанию безопасная и рассчитана на `https://`.

## Сборка

```bash
./gradlew assembleDebug
./scripts/build_release_apk.sh
```

Если хостовая Java слишком новая для текущего Android Gradle/Kotlin toolchain, задайте совместимую JDK 17 или 21 через `JAVA_HOME`.

Для production-подписи задайте:

```bash
export COREPOST_UPLOAD_STORE_FILE='<keystore-file>'
export COREPOST_UPLOAD_STORE_PASSWORD='...'
export COREPOST_UPLOAD_KEY_ALIAS='...'
export COREPOST_UPLOAD_KEY_PASSWORD='...'
```

Release APK появляется в `app/build/outputs/apk/release/`.

## Demo / Release

```bash
emulator -avd my_avd
adb wait-for-device
./scripts/install_demo_on_emulator.sh
```

Если сервер запущен на хосте и в приложении используется его локальный адрес, отдельно выполните:

```bash
adb reverse tcp:<PORT> tcp:<PORT>
```

Если нужен provisioning тестового устройства через admin API:

```bash
export COREPOST_ADMIN_TOKEN='<token>'
python3 scripts/provision_demo_device.py --base-url http://host-or-lan-server:PORT
```

Публикация release APK:

```bash
gh release create android-v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --repo CorePost/corepost-mobile-android \
  --title "CorePost Android v1.0.0" \
  --notes-file CHANGELOG.md
```

Demo assets:

- Скриншоты: [docs/media/screenshots/README.md](docs/media/screenshots/README.md)
- Видео: [docs/media/video/README.md](docs/media/video/README.md)

![Onboarding Hero](docs/media/screenshots/01-onboarding-current.png)
![Onboarding Form](docs/media/screenshots/02-onboarding-form.png)
![Onboarding Filled](docs/media/screenshots/03-onboarding-filled.png)
![Dashboard Without Server](docs/media/screenshots/04-dashboard-error.png)
![Registered](docs/media/screenshots/06-registered.png)
![Pending Lock](docs/media/screenshots/07-pending-lock.png)
![Locked](docs/media/screenshots/08-locked.png)
![Recovered](docs/media/screenshots/09-recovered.png)

- [demo-my_avd.mp4](docs/media/video/demo-my_avd.mp4) — живая запись сценария `recovered -> pending_lock -> locked -> recovered` на `my_avd`.

## QA / Known Limits

Проверочный минимум:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

Ограничения:

- `debug` оставляет cleartext только для локального/demo использования; `release` не должен использовать `http://`.
- Release signing использует debug keystore как fallback, пока production keystore не передан через env.
- Клиентский API-слой спроектирован консервативно, но серверный контракт может продолжать меняться, поэтому перед внешней публикацией стоит перепроверять совместимость demo/provisioning flow.
