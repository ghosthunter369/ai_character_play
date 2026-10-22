"""Offline basic speech bridge. No credentials and no cloud provider fallback."""
import io
import logging
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import wave
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, Field, field_validator

MAX_UPLOAD = 5 * 1024 * 1024
MAX_RESPONSE = 8 * 1024 * 1024
TEMP_ROOT = Path("/tmp/local-speech")
MODEL_NAME = os.environ.get("WHISPER_MODEL", "tiny")
MODEL_ROOT = os.environ.get("WHISPER_DOWNLOAD_ROOT", "/models")
ALLOWED_MODELS = {"tiny", "tiny.en", "base", "base.en", "small", "small.en", "medium", "medium.en"}
logger = logging.getLogger("local-speech")


def flag(name, default=False):
    return os.environ.get(name, str(default)).strip().lower() in {"true", "1", "yes"}


class ModelState:
    def __init__(self):
        self.model = None
        self.status = "not_loaded"
        self.slots = threading.Semaphore(1)
        self.tts_ready = shutil.which("espeak-ng") is not None

    def load(self):
        self.status = "loading"
        try:
            if MODEL_NAME not in ALLOWED_MODELS:
                raise ValueError("Unsupported configured model")
            from faster_whisper import WhisperModel
            model = WhisperModel(MODEL_NAME, device="cpu", compute_type="int8", num_workers=1,
                                 cpu_threads=max(1, min(8, int(os.environ.get("WHISPER_CPU_THREADS", "2")))),
                                 download_root=MODEL_ROOT, local_files_only=flag("WHISPER_LOCAL_FILES_ONLY"))
            self.model = model
            self.status = "ready"
            logger.info("Local Whisper model loaded on CPU/int8")
        except Exception:
            self.status = "failed"
            # Avoid exposing local paths, proxy credentials or signed download URLs.
            logger.error("Whisper model unavailable; preload the model cache and restart")


state = ModelState()


@asynccontextmanager
async def lifespan(application):
    TEMP_ROOT.mkdir(parents=True, exist_ok=True)
    if flag("WHISPER_PRELOAD", True):
        threading.Thread(target=state.load, daemon=True, name="whisper-loader").start()
    yield


class BodyLimit:
    """Bound the raw request before Starlette's multipart parser allocates files."""
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope.get("method") != "POST":
            return await self.app(scope, receive, send)
        limit = MAX_UPLOAD + 65536 if scope.get("path") == "/v1/audio/transcriptions" else 16384
        headers = dict(scope.get("headers", []))
        try:
            content_length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            return await JSONResponse({"detail": "Invalid request size"}, status_code=400)(scope, receive, send)
        if content_length > limit:
            return await JSONResponse({"detail": "Request too large"}, status_code=413)(scope, receive, send)
        body = bytearray()
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            body.extend(message.get("body", b""))
            if len(body) > limit:
                return await JSONResponse({"detail": "Request too large"}, status_code=413)(scope, receive, send)
            if not message.get("more_body", False):
                break
        delivered = False

        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}
            return await receive()
        return await self.app(scope, replay, send)


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None)
app.add_middleware(BodyLimit)


@app.get("/health")
def health():
    ready = state.model is not None and state.status == "ready" and state.tts_ready
    return JSONResponse({"ready": ready,
                         "asr": {"status": state.status, "model": MODEL_NAME, "device": "cpu", "compute_type": "int8"},
                         "tts": {"ready": state.tts_ready, "engine": "espeak-ng", "voice": "cmn", "quality": "basic mechanical voice"}},
                        status_code=200 if ready else 503)


def validate_wav(data):
    if not data or len(data) > MAX_UPLOAD:
        raise HTTPException(413, "Audio must be 1..5MiB")
    try:
        with wave.open(io.BytesIO(data), "rb") as audio:
            rate, frames = audio.getframerate(), audio.getnframes()
            if audio.getcomptype() != "NONE" or audio.getsampwidth() != 2 or audio.getnchannels() not in (1, 2) or not 8000 <= rate <= 48000:
                raise HTTPException(400, "Expected 16-bit PCM WAV, 8..48kHz, one or two channels")
            if frames == 0 or frames / rate > 30:
                raise HTTPException(400, "Audio duration must be greater than 0 and at most 30 seconds")
            expected = frames * audio.getnchannels() * audio.getsampwidth()
            if len(audio.readframes(frames)) != expected:
                raise HTTPException(400, "Incomplete WAV audio")
    except (wave.Error, EOFError, ValueError):
        raise HTTPException(400, "Invalid PCM WAV audio") from None


@app.post("/v1/audio/transcriptions")
def transcribe(file: UploadFile = File(...), model: str = Form("whisper-1")):
    try:
        if model not in {"whisper-1", MODEL_NAME}:
            raise HTTPException(400, "Only the preconfigured local model is supported")
        data = file.file.read(MAX_UPLOAD + 1)
        validate_wav(data)
    finally:
        file.file.close()
    if not state.slots.acquire(blocking=False):
        raise HTTPException(429, "Local recognition is busy; retry after the current request", headers={"Retry-After": "1"})
    try:
        if state.model is None or state.status != "ready":
            raise HTTPException(503, "Whisper model is not loaded; preload cache and restart")
        with tempfile.TemporaryDirectory(prefix="request-", dir=TEMP_ROOT) as directory:
            sample = Path(directory) / "input.wav"
            sample.write_bytes(data)
            segments, _ = state.model.transcribe(str(sample), beam_size=3, vad_filter=True,
                                                  condition_on_previous_text=False, max_new_tokens=256)
            text = "".join(segment.text for segment in segments).strip()
        return {"text": text}
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(503, "Local recognition failed") from None
    finally:
        state.slots.release()


class SpeechRequest(BaseModel):
    input: str = Field(min_length=1, max_length=600)
    voice: str = "alloy"
    response_format: str = "wav"
    model: str = "tts-1"

    @field_validator("input")
    @classmethod
    def nonblank(cls, value):
        if not value.strip():
            raise ValueError("Text must not be blank")
        return value


@app.post("/v1/audio/speech")
def synthesize(payload: SpeechRequest):
    if payload.voice not in {"alloy", "cmn", "zh", ""} or payload.response_format != "wav":
        raise HTTPException(400, "Only the local Chinese voice and WAV format are supported")
    if not state.tts_ready:
        raise HTTPException(503, "Local speech engine unavailable")
    try:
        with tempfile.TemporaryDirectory(prefix="request-", dir=TEMP_ROOT) as directory:
            output = Path(directory) / "output.wav"
            result = subprocess.run(["espeak-ng", "-v", "cmn", "-s", "160", "-w", str(output), "--stdin"],
                                    input=payload.input.encode("utf-8"), stdout=subprocess.DEVNULL,
                                    stderr=subprocess.DEVNULL, timeout=20, check=False)
            if result.returncode != 0 or not output.is_file() or output.stat().st_size > MAX_RESPONSE:
                raise HTTPException(503, "Local speech generation failed")
            audio = output.read_bytes()
        if len(audio) < 44 or audio[:4] != b"RIFF":
            raise HTTPException(503, "Local speech engine produced invalid audio")
        return Response(audio, media_type="audio/wav")
    except HTTPException:
        raise
    except (OSError, subprocess.SubprocessError):
        raise HTTPException(503, "Local speech generation failed") from None
