# Insta360 X5 — снимок по Wi‑Fi (OSC)

Скрипт `take_photo.py` делает фото через официальный **Insta360 OSC API**  
(документация: [Insta360Develop/Insta360_OSC](https://github.com/Insta360Develop/Insta360_OSC)).

## Подготовка

1. Активируйте камеру в официальном приложении Insta360 (иначе `unactivated`).
2. Вставьте SD‑карту.
3. Включите Wi‑Fi AP на камере.
4. Подключите ПК к Wi‑Fi камеры (пароль по умолчанию обычно `88888888`).
5. IP камеры: **`192.168.42.1`**

## Запуск

```bash
python take_photo.py
```

Опции:

```bash
# Склейка на камере + HDR
python take_photo.py --stitch ondevice --hdr hdr --out ./photos

# Только снимок, без скачивания на ПК
python take_photo.py --no-download

# Другой IP (если нужно)
python take_photo.py --host 192.168.42.1
```

## Что делает скрипт

1. `GET /osc/info` — модель / прошивка  
2. `POST /osc/state` — карта и батарея  
3. `camera.setOptions` — `captureMode=image`, stitching, HDR  
4. `camera.takePicture`  
5. Поллинг `/osc/commands/status` до `done`  
6. Скачивание файла по `fileUrl` в `./photos`
