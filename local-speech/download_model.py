"""Download the official configured Whisper model and prove CPU/int8 loading."""
import os
from faster_whisper import WhisperModel

name = os.environ.get("WHISPER_MODEL", "tiny")
if name not in {"tiny", "tiny.en", "base", "base.en", "small", "small.en", "medium", "medium.en"}:
    raise SystemExit("Unsupported model; use an official Systran size name")
WhisperModel(name, device="cpu", compute_type="int8", cpu_threads=2,
             download_root=os.environ.get("WHISPER_DOWNLOAD_ROOT", "/models"),
             local_files_only=os.environ.get("WHISPER_LOCAL_FILES_ONLY", "false").lower() in {"true", "1"})
print("Official Whisper model cached and loaded successfully on CPU/int8")
