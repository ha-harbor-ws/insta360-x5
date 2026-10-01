#!/usr/bin/env python3
"""
Снимок с Insta360 X5 через официальный OSC API (Wi‑Fi).

Перед запуском:
  1. Включите Wi‑Fi на камере (кнопка / меню).
  2. Подключите ПК к точке доступа камеры (пароль по умолчанию: 88888888).
  3. IP камеры: 192.168.42.1

Пример:
  python take_photo.py          # интерактивное меню
  python take_photo.py --once   # один снимок и выход
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DEFAULT_HOST = "192.168.42.1"
DEFAULT_PORT = 80
POLL_INTERVAL_SEC = 1.0
STATUS_TIMEOUT_SEC = 120.0

# max длина длинной стороны; None = без изменения размера
RESOLUTION_PRESETS: dict[str, int | None] = {
    "original": None,
    "8k": 7680,
    "6k": 6144,
    "4k": 3840,
    "2k": 2048,
    "1080": 1920,
    "720": 1280,
}

QUALITY_PRESETS: tuple[int, ...] = (95, 90, 85, 80, 75, 60, 50)


class OscError(RuntimeError):
    """Ошибка ответа OSC API."""


@dataclass
class Settings:
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    out_dir: Path = Path("photos")
    stitch: str = "ondevice"
    hdr: str = "off"
    download: bool = True
    delete_after_download: bool = False
    compress: bool = False
    compress_resolution: str = "4k"
    compress_quality: int = 85
    compress_keep_original: bool = False


class Insta360Osc:
    def __init__(self, host: str = DEFAULT_HOST, port: int = DEFAULT_PORT, timeout: float = 30.0):
        self.base = f"http://{host}:{port}"
        self.timeout = timeout

    def _headers(self, body: bytes | None = None) -> dict[str, str]:
        headers = {
            "Accept": "application/json",
            "X-XSRF-Protected": "1",
        }
        if body is not None:
            headers["Content-Type"] = "application/json;charset=utf-8"
            headers["Content-Length"] = str(len(body))
        return headers

    def _request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        raw: bool = False,
    ) -> Any:
        url = f"{self.base}{path}"
        body: bytes | None = None
        if payload is not None:
            body = json.dumps(payload).encode("utf-8")

        req = Request(url, data=body, headers=self._headers(body), method=method)
        try:
            with urlopen(req, timeout=self.timeout) as resp:
                data = resp.read()
                if raw:
                    return data
                if not data:
                    return {}
                return json.loads(data.decode("utf-8"))
        except HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise OscError(f"HTTP {exc.code} {path}: {detail}") from exc
        except URLError as exc:
            raise OscError(
                f"Нет связи с камерой ({self.base}). "
                f"Проверьте Wi‑Fi подключение к AP камеры. Причина: {exc.reason}"
            ) from exc

    def info(self) -> dict[str, Any]:
        return self._request("GET", "/osc/info")

    def state(self) -> dict[str, Any]:
        return self._request("POST", "/osc/state")

    def execute(self, payload: dict[str, Any]) -> dict[str, Any]:
        result = self._request("POST", "/osc/commands/execute", payload)
        self._raise_if_error(result)
        return result

    def command_status(self, command_id: str) -> dict[str, Any]:
        result = self._request("POST", "/osc/commands/status", {"id": command_id})
        self._raise_if_error(result)
        return result

    def download(self, file_url: str) -> bytes:
        req = Request(file_url, headers={"Accept": "*/*"}, method="GET")
        try:
            with urlopen(req, timeout=self.timeout) as resp:
                return resp.read()
        except HTTPError as exc:
            raise OscError(f"Не удалось скачать {file_url}: HTTP {exc.code}") from exc
        except URLError as exc:
            raise OscError(f"Не удалось скачать {file_url}: {exc.reason}") from exc

    def list_files(self, file_type: str = "image", entry_count: int = 10) -> dict[str, Any]:
        return self.execute(
            {
                "name": "camera.listFiles",
                "parameters": {
                    "fileType": file_type,
                    "entryCount": entry_count,
                    "maxThumbSize": 0,
                },
            }
        )

    def delete_files(self, file_urls: list[str]) -> dict[str, Any]:
        if not file_urls:
            raise OscError("Нет URL для удаления")
        return self.execute(
            {
                "name": "camera.delete",
                "parameters": {"fileUrls": file_urls},
            }
        )

    @staticmethod
    def _raise_if_error(result: dict[str, Any]) -> None:
        if result.get("state") == "error" or "error" in result:
            err = result.get("error") or {}
            code = err.get("code", "unknown")
            message = err.get("message", json.dumps(result, ensure_ascii=False))
            if code == "unactivated":
                raise OscError(
                    "Камера не активирована. Откройте официальное приложение Insta360 "
                    "и активируйте устройство."
                )
            if code == "disabledCommand":
                raise OscError(
                    "Команда недоступна в текущем режиме. "
                    f"Детали: {message}"
                )
            raise OscError(f"OSC error [{code}]: {message}")


def print_camera_info(cam: Insta360Osc) -> None:
    info = cam.info()
    state = cam.state()
    st = state.get("state", {})
    battery = st.get("batteryLevel", "?")
    if isinstance(battery, (int, float)):
        battery_str = f"{battery * 100:.0f}%"
    else:
        battery_str = str(battery)

    print(f"  URL:      {cam.base}")
    print(f"  Модель:   {info.get('model', '?')}")
    print(f"  Прошивка: {info.get('firmwareVersion', '?')}")
    print(f"  S/N:      {info.get('serialNumber', '?')}")
    print(f"  Карта:    {st.get('_cardState', '?')}")
    print(f"  Батарея:  {battery_str}")
    print(f"  Storage:  {st.get('storageUri', '?')}")


def take_photo(cam: Insta360Osc, settings: Settings) -> list[Path]:
    print(f"[1/5] Подключение к {cam.base} ...")
    print_camera_info(cam)

    state = cam.state()
    card = state.get("state", {}).get("_cardState", "?")
    if card in ("noCard", "noSpace", "invalidFormat", "writeProtect", "otherError"):
        raise OscError(f"Проблема с картой памяти: {card}")

    print("\n[2/5] Проверка поддержки on-device stitching ...")
    options = cam.execute(
        {
            "name": "camera.getOptions",
            "parameters": {
                "optionNames": [
                    "captureMode",
                    "hdr",
                    "hdrSupport",
                    "photoStitching",
                    "photoStitchingSupport",
                ]
            },
        }
    )
    opts = options.get("results", {}).get("options", {})
    stitch_support = opts.get("photoStitchingSupport") or ["none"]
    stitch = settings.stitch
    print(f"      photoStitchingSupport: {stitch_support}")
    print(
        f"      текущие: captureMode={opts.get('captureMode')}, "
        f"hdr={opts.get('hdr')}, photoStitching={opts.get('photoStitching')}"
    )

    if stitch != "none" and stitch not in stitch_support:
        print(f"      Внимание: stitch={stitch} не поддерживается, используем 'none'")
        stitch = "none"

    print(f"\n[3/5] setOptions: captureMode=image, hdr={settings.hdr}, photoStitching={stitch}")
    set_opts: dict[str, Any] = {
        "captureMode": "image",
        "hdr": settings.hdr,
    }
    if "ondevice" in stitch_support or "none" in stitch_support:
        set_opts["photoStitching"] = stitch

    cam.execute({"name": "camera.setOptions", "parameters": {"options": set_opts}})

    print("\n[4/5] camera.takePicture ...")
    take = cam.execute({"name": "camera.takePicture"})
    command_id = take.get("id")
    if not command_id:
        if take.get("state") == "done":
            results = take.get("results", {})
        else:
            raise OscError(f"Нет id команды в ответе: {take}")
    else:
        print(f"      id={command_id}, ожидание завершения...")
        deadline = time.monotonic() + STATUS_TIMEOUT_SEC
        results = {}
        while True:
            status = cam.command_status(command_id)
            state_name = status.get("state")
            progress = status.get("progress", {}).get("completion")
            if progress is not None:
                print(f"      progress: {progress:.0%}", end="\r")
            if state_name == "done":
                print()
                results = status.get("results", {})
                break
            if time.monotonic() > deadline:
                raise OscError("Таймаут ожидания снимка")
            time.sleep(POLL_INTERVAL_SEC)

    file_urls: list[str] = []
    if results.get("fileUrl"):
        file_urls.append(results["fileUrl"])
    for url in results.get("_fileGroup") or []:
        if url not in file_urls:
            file_urls.append(url)

    if not file_urls:
        raise OscError(f"Снимок готов, но URL файлов не найдены: {results}")

    print("\n[5/5] Файлы на камере:")
    for url in file_urls:
        print(f"      {url}")

    saved: list[Path] = []
    if settings.download:
        settings.out_dir.mkdir(parents=True, exist_ok=True)
        for url in file_urls:
            name = url.rstrip("/").split("/")[-1]
            dest = settings.out_dir / name
            print(f"      Скачивание → {dest}")
            data = cam.download(url)
            dest.write_bytes(data)
            print(f"      OK ({len(data)} байт)")
            dest = maybe_compress(dest, settings)
            saved.append(dest)
        if settings.delete_after_download and saved:
            print("      Удаление с камеры после скачивания...")
            delete_urls(cam, file_urls)
    else:
        print("      Скачивание пропущено")

    return saved


def fetch_image_entries(cam: Insta360Osc, count: int = 20) -> tuple[list[dict[str, Any]], int]:
    result = cam.list_files(file_type="image", entry_count=count)
    entries = result.get("results", {}).get("entries") or []
    total = int(result.get("results", {}).get("totalEntries", len(entries)))
    return entries, total


def print_file_entries(entries: list[dict[str, Any]], total: int) -> None:
    print(f"Всего изображений: {total}. Показано: {len(entries)}\n")
    if not entries:
        print("  (пусто)")
        return
    for i, entry in enumerate(entries, 1):
        name = entry.get("name", "?")
        size = entry.get("size", 0)
        wh = f"{entry.get('width', '?')}x{entry.get('height', '?')}"
        dt = entry.get("dateTimeZone", entry.get("dateTime", "?"))
        print(f"  {i}. {name}")
        print(f"     {wh} | {size} байт | {dt}")
        print(f"     {entry.get('fileUrl', '')}")


def entry_urls(entries: list[dict[str, Any]], indexes: list[int]) -> list[str]:
    urls: list[str] = []
    for idx in indexes:
        url = entries[idx - 1].get("fileUrl")
        if not url:
            raise OscError(f"У файла #{idx} нет fileUrl")
        urls.append(url)
    return urls


def delete_urls(cam: Insta360Osc, file_urls: list[str]) -> None:
    print(f"  Удаление с камеры ({len(file_urls)}):")
    for url in file_urls:
        print(f"    - {url}")
    cam.delete_files(file_urls)
    print("  Удалено.")


def _load_pillow():
    try:
        from PIL import Image  # type: ignore
    except ImportError as exc:
        raise OscError(
            "Для сжатия нужен пакет Pillow. Установите: pip install Pillow"
        ) from exc
    return Image


def compress_summary(settings: Settings) -> str:
    if not settings.compress:
        return "off"
    keep = "keep" if settings.compress_keep_original else "replace"
    return f"{settings.compress_resolution}/q{settings.compress_quality}/{keep}"


def compressed_path(src: Path, settings: Settings) -> Path:
    res = settings.compress_resolution
    quality = settings.compress_quality
    return src.with_name(f"{src.stem}_{res}_q{quality}.jpg")


def compress_image(src: Path, settings: Settings) -> Path:
    Image = _load_pillow()
    if settings.compress_resolution not in RESOLUTION_PRESETS:
        raise OscError(f"Неизвестный пресет разрешения: {settings.compress_resolution}")
    max_side = RESOLUTION_PRESETS[settings.compress_resolution]

    with Image.open(src) as img:
        img.load()
        width, height = img.size
        work = img.copy()

    if max_side is not None and max(width, height) > max_side:
        if width >= height:
            new_w = max_side
            new_h = max(1, round(height * max_side / width))
        else:
            new_h = max_side
            new_w = max(1, round(width * max_side / height))
        resample = getattr(getattr(Image, "Resampling", Image), "LANCZOS", Image.LANCZOS)
        work = work.resize((new_w, new_h), resample)

    if work.mode not in ("RGB", "L"):
        work = work.convert("RGB")

    if settings.compress_keep_original:
        dest = compressed_path(src, settings)
    else:
        dest = src if src.suffix.lower() in (".jpg", ".jpeg") else src.with_suffix(".jpg")

    before = src.stat().st_size
    out_w, out_h = work.size
    tmp = dest.with_suffix(dest.suffix + ".tmp")
    try:
        work.save(
            tmp,
            format="JPEG",
            quality=settings.compress_quality,
            optimize=True,
            progressive=True,
        )
        tmp.replace(dest)
    finally:
        if tmp.exists():
            tmp.unlink(missing_ok=True)
        work.close()

    after = dest.stat().st_size
    print(
        f"  Сжатие: {width}x{height} → {out_w}x{out_h}, "
        f"q={settings.compress_quality}, {before} → {after} байт"
    )
    print(f"  Файл: {dest}")

    if not settings.compress_keep_original and dest.resolve() != src.resolve() and src.exists():
        src.unlink()
    return dest


def maybe_compress(path: Path, settings: Settings) -> Path:
    if not settings.compress:
        return path
    return compress_image(path, settings)


def download_entry(cam: Insta360Osc, entry: dict[str, Any], settings: Settings) -> Path:
    file_url = entry.get("fileUrl")
    if not file_url:
        raise OscError(f"У файла нет fileUrl: {entry.get('name', '?')}")
    name = entry.get("name") or file_url.rstrip("/").split("/")[-1]
    settings.out_dir.mkdir(parents=True, exist_ok=True)
    dest = settings.out_dir / name
    print(f"  Скачивание {name} → {dest}")
    data = cam.download(file_url)
    dest.write_bytes(data)
    print(f"  OK ({len(data)} байт)")
    return maybe_compress(dest, settings)


def parse_selection(raw: str, max_index: int) -> list[int] | None:
    """Разбор выбора: '1', '1,3', '1-3', 'all'. None = отмена/назад."""
    text = raw.strip().lower()
    if not text or text in ("0", "q", "n", "нет", "back"):
        return None
    if text in ("a", "all", "все", "*"):
        return list(range(1, max_index + 1))

    selected: set[int] = set()
    for part in text.replace(" ", "").split(","):
        if not part:
            continue
        if "-" in part:
            left, _, right = part.partition("-")
            try:
                start = int(left)
                end = int(right)
            except ValueError as exc:
                raise ValueError(f"Неверный диапазон: {part}") from exc
            if start > end:
                start, end = end, start
            for n in range(start, end + 1):
                if 1 <= n <= max_index:
                    selected.add(n)
                else:
                    raise ValueError(f"Номер вне диапазона: {n}")
        else:
            try:
                n = int(part)
            except ValueError as exc:
                raise ValueError(f"Неверный номер: {part}") from exc
            if not 1 <= n <= max_index:
                raise ValueError(f"Номер вне диапазона: {n}")
            selected.add(n)
    return sorted(selected)


def ask_indexes(entries: list[dict[str, Any]], action_label: str) -> list[int] | None:
    print(
        f"{action_label}: номер (1), список (1,3), диапазон (1-3), все (all), 0 — отмена"
    )
    raw = prompt("Выбор файлов", "0")
    try:
        return parse_selection(raw, len(entries))
    except ValueError as exc:
        print(f"Ошибка выбора: {exc}")
        return None


def browse_camera_files(cam: Insta360Osc, settings: Settings) -> None:
    while True:
        count_raw = prompt("Сколько последних файлов показать", "20")
        try:
            count = max(1, int(count_raw))
        except ValueError:
            print("Нужно целое число.")
            continue

        entries, total = fetch_image_entries(cam, count=count)
        print()
        print_file_entries(entries, total)
        if not entries:
            return

        print()
        print("Действие: d — скачать, x — удалить, 0 — назад")
        if settings.delete_after_download:
            print("(в настройках включено: удалять с камеры после скачивания)")
        action = prompt("Выбор", "0").strip().lower()

        if action in ("0", "q", "back", ""):
            return

        if action in ("d", "download", "с", "скачать"):
            indexes = ask_indexes(entries, "Скачать")
            if not indexes:
                continue
            urls = entry_urls(entries, indexes)
            saved: list[Path] = []
            print()
            for idx in indexes:
                saved.append(download_entry(cam, entries[idx - 1], settings))
            print("\nСкачано:")
            for path in saved:
                print(f"  {path.resolve()}")
            if settings.delete_after_download:
                print()
                delete_urls(cam, urls)
            again = prompt("\nЕщё операция с файлами? (y/n)", "y").lower()
            if again not in ("y", "yes", "д", "да", "1"):
                return
            continue

        if action in ("x", "del", "delete", "у", "удалить"):
            indexes = ask_indexes(entries, "Удалить с камеры")
            if not indexes:
                continue
            urls = entry_urls(entries, indexes)
            print()
            print("Будут удалены:")
            for idx in indexes:
                entry = entries[idx - 1]
                print(f"  {idx}. {entry.get('name', entry.get('fileUrl', '?'))}")
            confirm = prompt("Подтвердить удаление? (y/n)", "n").lower()
            if confirm not in ("y", "yes", "д", "да", "1"):
                print("Отменено.")
                continue
            print()
            delete_urls(cam, urls)
            again = prompt("\nЕщё операция с файлами? (y/n)", "y").lower()
            if again not in ("y", "yes", "д", "да", "1"):
                return
            continue

        print("Неизвестное действие. Используйте d, x или 0.")


def prompt(text: str, default: str | None = None) -> str:
    suffix = f" [{default}]" if default is not None else ""
    value = input(f"{text}{suffix}: ").strip()
    if not value and default is not None:
        return default
    return value


def pause() -> None:
    input("\nНажмите Enter, чтобы вернуться в меню...")


def show_settings(settings: Settings) -> None:
    print("Текущие настройки:")
    print(f"  1) Host / Port              : {settings.host}:{settings.port}")
    print(f"  2) Папка                    : {settings.out_dir.resolve()}")
    print(f"  3) Stitching                : {settings.stitch}")
    print(f"  4) HDR                      : {settings.hdr}")
    print(f"  5) Скачивать после снимка   : {'да' if settings.download else 'нет'}")
    print(f"  6) Удалять после скачивания : {'да' if settings.delete_after_download else 'нет'}")
    print(f"  7) Сжатие                   : {'да' if settings.compress else 'нет'}")
    print(f"  8) Разрешение сжатия        : {settings.compress_resolution}")
    print(f"  9) Качество JPEG            : {settings.compress_quality}")
    print(f" 10) Хранить оригинал         : {'да' if settings.compress_keep_original else 'нет'}")
    print("  0) Назад")


def choose_from_list(title: str, labels: list[str], current: str) -> str | None:
    print(f"\n{title}")
    current_idx = None
    for i, label in enumerate(labels, 1):
        mark = " *" if label == current or label.startswith(f"{current} ") else ""
        if label == current or label.startswith(f"{current} "):
            current_idx = i
        print(f"  {i}) {label}{mark}")
    print("  0) Отмена")
    raw = prompt("Выбор", str(current_idx or 0))
    if raw in ("0", ""):
        return None
    try:
        idx = int(raw)
    except ValueError:
        print("Нужен номер пункта.")
        return None
    if not 1 <= idx <= len(labels):
        print("Номер вне диапазона.")
        return None
    return labels[idx - 1]


def edit_compress_resolution(settings: Settings) -> None:
    labels = []
    keys = list(RESOLUTION_PRESETS.keys())
    for key in keys:
        max_side = RESOLUTION_PRESETS[key]
        if max_side is None:
            labels.append(f"{key} (без ресайза)")
        else:
            labels.append(f"{key} (длинная сторона ≤ {max_side}px)")
    current_label = next(
        (lbl for key, lbl in zip(keys, labels) if key == settings.compress_resolution),
        labels[0],
    )
    chosen = choose_from_list("Разрешение сжатия", labels, current_label)
    if chosen is None:
        return
    settings.compress_resolution = keys[labels.index(chosen)]
    settings.compress = True
    print(f"Выбрано: {settings.compress_resolution}")


def edit_compress_quality(settings: Settings) -> None:
    labels = [str(q) for q in QUALITY_PRESETS]
    current = str(settings.compress_quality)
    if current not in labels:
        labels = [current, *labels]
    chosen = choose_from_list("Качество JPEG (выше = лучше/больше)", labels, current)
    if chosen is None:
        return
    settings.compress_quality = int(chosen)
    settings.compress = True
    print(f"Выбрано: q={settings.compress_quality}")


def edit_settings(settings: Settings) -> None:
    while True:
        print()
        show_settings(settings)
        choice = prompt("Выбор", "0")
        if choice == "0":
            return
        if choice == "1":
            settings.host = prompt("IP камеры", settings.host)
            port_raw = prompt("Порт", str(settings.port))
            try:
                settings.port = int(port_raw)
            except ValueError:
                print("Неверный порт, оставлено без изменений.")
        elif choice == "2":
            settings.out_dir = Path(prompt("Папка для снимков", str(settings.out_dir)))
        elif choice == "3":
            value = prompt("Stitching (none/ondevice)", settings.stitch).lower()
            if value in ("none", "ondevice"):
                settings.stitch = value
            else:
                print("Допустимо: none или ondevice")
        elif choice == "4":
            value = prompt("HDR (off/hdr)", settings.hdr).lower()
            if value in ("off", "hdr"):
                settings.hdr = value
            else:
                print("Допустимо: off или hdr")
        elif choice == "5":
            value = prompt("Скачивать файлы? (y/n)", "y" if settings.download else "n").lower()
            settings.download = value in ("y", "yes", "д", "да", "1")
        elif choice == "6":
            value = prompt(
                "Удалять файл с камеры после скачивания? (y/n)",
                "y" if settings.delete_after_download else "n",
            ).lower()
            settings.delete_after_download = value in ("y", "yes", "д", "да", "1")
        elif choice == "7":
            value = prompt(
                "Сжимать скачанные файлы? (y/n)",
                "y" if settings.compress else "n",
            ).lower()
            settings.compress = value in ("y", "yes", "д", "да", "1")
        elif choice == "8":
            edit_compress_resolution(settings)
        elif choice == "9":
            edit_compress_quality(settings)
        elif choice == "10":
            value = prompt(
                "Оставлять оригинал рядом со сжатым? (y/n)",
                "y" if settings.compress_keep_original else "n",
            ).lower()
            settings.compress_keep_original = value in ("y", "yes", "д", "да", "1")
        else:
            print("Неизвестный пункт.")


def print_menu(settings: Settings) -> None:
    print()
    print("=" * 44)
    print("  Insta360 X5 — OSC remote")
    print("=" * 44)
    print(f"  Камера: {settings.host}:{settings.port}")
    print(f"  Режим:  stitch={settings.stitch} | hdr={settings.hdr} | "
          f"download={'on' if settings.download else 'off'} | "
          f"del-after={'on' if settings.delete_after_download else 'off'} | "
          f"compress={compress_summary(settings)}")
    print("-" * 44)
    print("  1) Сделать снимок")
    print("  2) Информация о камере")
    print("  3) Файлы на камере / скачать / удалить")
    print("  4) Настройки")
    print("  0) Выход")
    print("-" * 44)


def run_menu(settings: Settings) -> int:
    while True:
        print_menu(settings)
        try:
            choice = prompt("Выбор", "1")
        except (EOFError, KeyboardInterrupt):
            print("\nВыход.")
            return 0

        cam = Insta360Osc(host=settings.host, port=settings.port)

        try:
            if choice == "0":
                print("Выход.")
                return 0
            if choice == "1":
                saved = take_photo(cam, settings)
                if saved:
                    print("\nГотово. Сохранено:")
                    for path in saved:
                        print(f"  {path.resolve()}")
                else:
                    print("\nГотово. Снимок остался на карте камеры.")
                pause()
            elif choice == "2":
                print()
                print_camera_info(cam)
                pause()
            elif choice == "3":
                print()
                browse_camera_files(cam, settings)
                pause()
            elif choice == "4":
                edit_settings(settings)
            else:
                print("Неизвестный пункт меню.")
                pause()
        except OscError as exc:
            print(f"\nОшибка: {exc}", file=sys.stderr)
            pause()
        except KeyboardInterrupt:
            print("\nПрервано.", file=sys.stderr)
            pause()


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Снимок с Insta360 X5 через OSC Wi‑Fi API")
    p.add_argument("--host", default=DEFAULT_HOST, help="IP камеры (по умолчанию 192.168.42.1)")
    p.add_argument("--port", type=int, default=DEFAULT_PORT, help="HTTP порт OSC")
    p.add_argument("--out", type=Path, default=Path("photos"), help="Папка для снимков")
    p.add_argument(
        "--stitch",
        choices=["none", "ondevice"],
        default="ondevice",
        help="Склейка на камере",
    )
    p.add_argument("--hdr", choices=["off", "hdr"], default="off", help="HDR: off или hdr")
    p.add_argument("--no-download", action="store_true", help="Не скачивать файл на ПК")
    p.add_argument(
        "--delete-after-download",
        action="store_true",
        help="Удалить файл с камеры после успешного скачивания",
    )
    p.add_argument("--compress", action="store_true", help="Сжимать скачанные JPEG")
    p.add_argument(
        "--compress-res",
        choices=list(RESOLUTION_PRESETS.keys()),
        default="4k",
        help="Пресет разрешения сжатия",
    )
    p.add_argument(
        "--compress-quality",
        type=int,
        default=85,
        help="Качество JPEG 1-95",
    )
    p.add_argument(
        "--compress-keep-original",
        action="store_true",
        help="Оставлять оригинал рядом со сжатым файлом",
    )
    p.add_argument(
        "--once",
        action="store_true",
        help="Один снимок без меню и выход",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    quality = args.compress_quality
    if not 1 <= quality <= 95:
        print("Ошибка: --compress-quality должен быть в диапазоне 1..95", file=sys.stderr)
        return 2
    settings = Settings(
        host=args.host,
        port=args.port,
        out_dir=args.out,
        stitch=args.stitch,
        hdr=args.hdr,
        download=not args.no_download,
        delete_after_download=args.delete_after_download,
        compress=args.compress,
        compress_resolution=args.compress_res,
        compress_quality=quality,
        compress_keep_original=args.compress_keep_original,
    )

    if args.once:
        cam = Insta360Osc(host=settings.host, port=settings.port)
        try:
            saved = take_photo(cam, settings)
        except OscError as exc:
            print(f"Ошибка: {exc}", file=sys.stderr)
            return 1
        except KeyboardInterrupt:
            print("\nПрервано", file=sys.stderr)
            return 130

        if saved:
            print("Готово. Сохранено:")
            for path in saved:
                print(f"  {path.resolve()}")
        else:
            print("Готово. Снимок остался на карте камеры.")
        return 0

    return run_menu(settings)


if __name__ == "__main__":
    raise SystemExit(main())
