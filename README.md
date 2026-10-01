# Insta360 X5 — OSC remote

Управление камерой **Insta360 X5** по Wi‑Fi через официальный [OSC API](https://github.com/Insta360Develop/Insta360_OSC).

В репозитории:
- **`take_photo.py`** — CLI / интерактивное меню для ПК
- **`android/`** — приложение для телефона (APK)

IP камеры в режиме AP: **`192.168.42.1`** (пароль Wi‑Fi обычно `88888888`).

---

## Подготовка

1. Активируйте камеру в официальном приложении Insta360.
2. Вставьте SD‑карту.
3. Включите Wi‑Fi на камере.
4. Подключите ПК или телефон к точке доступа камеры.

---

## Python (`take_photo.py`)

Зависимость для сжатия: `pip install -r requirements.txt` (Pillow).

```bash
python take_photo.py          # меню
python take_photo.py --once   # один снимок и выход
```

### Меню

| Пункт | Действие |
|------|----------|
| 1 | Сделать снимок (опционально скачать и сжать) |
| 2 | Информация о камере |
| 3 | Файлы на камере: скачать / удалить |
| 4 | Настройки |
| 0 | Выход |

### Настройки

- Host / port
- Stitching: `ondevice` / `none`
- HDR: `off` / `hdr`
- Скачивать после снимка
- Удалять файл с камеры после скачивания
- Сжатие JPEG: разрешение (`original`…`720`), качество (`95`…`50`), хранить оригинал

### CLI

```bash
python take_photo.py --once --stitch ondevice --hdr off --out ./photos
python take_photo.py --once --compress --compress-res 4k --compress-quality 85
python take_photo.py --once --delete-after-download
```

---

## Android

- **minSdk 26** (Android 8.0) … **targetSdk 36** (Android 16)
- Готовый APK: [`Insta360X5-debug.apk`](./Insta360X5-debug.apk)

### Меню приложения

1. **Сделать снимок**
2. **Информация о камере**
3. **Файлы на камере** — список, скачать, удалить, кнопка «Открыть галерею»
4. **Скачанные фото** — сетка и полноэкранный просмотр (свайп)
5. **Настройки** — host, stitch, HDR, скачивание, удаление после скачивания, сжатие

После скачивания (снимок или файлы с камеры) предлагается открыть галерею.

### Сборка

```bat
cd android
gradlew.bat assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

Подробнее: [android/README.md](./android/README.md)

---

## OSC-последовательность снимка

1. `GET /osc/info` — модель / прошивка  
2. `POST /osc/state` — карта и батарея  
3. `camera.setOptions` — `captureMode=image`, stitching, HDR  
4. `camera.takePicture`  
5. Поллинг `/osc/commands/status` до `done`  
6. Скачивание по `fileUrl` (и опционально `camera.delete`)
