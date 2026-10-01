# Insta360 X5 — Android APK

Приложение для телефона с OSC-функционалом `take_photo.py`.

- **minSdk:** 26 (Android 8.0)
- **target/compileSdk:** 36 (Android 16)

## Возможности

1. Снимок
2. Информация о камере
3. Файлы на камере (скачать / удалить) + кнопка «Открыть галерею»
4. **Скачанные фото** — просмотр сеткой и полноэкранно
5. Настройки (host, stitch, HDR, сжатие, удаление после скачивания)

После скачивания предлагается открыть галерею.

## APK

Скачать (постоянная ссылка latest):  
https://github.com/ha-harbor-ws/insta360-x5/releases/latest/download/Insta360X5-debug.apk

Собрать заново:

```bat
cd android
gradlew.bat assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Использование

1. Подключите телефон к Wi‑Fi камеры Insta360 (пароль обычно `88888888`).
2. Установите APK (разрешите установку из неизвестных источников).
3. Откройте приложение и работайте с меню.
