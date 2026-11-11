# Insta360 X5 — Android APK

Приложение для телефона с OSC-функционалом `take_photo.py`.

- **minSdk:** 26 (Android 8.0)
- **target/compileSdk:** 36 (Android 16)
- **version:** 1.0.2

## Возможности

1. Большая круглая кнопка снимка + диалог «Номер колодца» (имя файла)
2. Сохранение в галерею телефона (`Pictures/Insta360X5`)
3. Информация о камере (перезапись вывода)
4. Файлы на камере (скачать / удалить) + галерея
5. Просмотр фото: номер колодца из имени файла, «Поделиться»
6. Настройки (host, stitch, HDR, сжатие 1080 по умолчанию)

## APK

Скачать (постоянная ссылка latest):  
https://github.com/ha-harbor-ws/insta360-x5/releases/latest/download/Insta360X5-debug.apk

Релиз: [v1.0.2](https://github.com/ha-harbor-ws/insta360-x5/releases/tag/v1.0.2)

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
