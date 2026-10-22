"""Prepare the official Whisper human-speech fixture, never an accuracy benchmark."""
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import wave
import av

URL = "https://raw.githubusercontent.com/openai/whisper/main/tests/jfk.flac"
request = urllib.request.Request(URL, headers={"User-Agent": "local-speech-test-fixture"})
with urllib.request.urlopen(request, timeout=60) as response:
    source = response.read(2 * 1024 * 1024 + 1)
if len(source) > 2 * 1024 * 1024 or not source.startswith(b"fLaC"):
    raise SystemExit("Invalid official speech sample response")
resampler = av.AudioResampler(format="s16", layout="mono", rate=16000)
parts = []
with av.open(io.BytesIO(source)) as container:
    for frame in container.decode(audio=0):
        parts.extend(converted.to_ndarray().tobytes() for converted in resampler.resample(frame))
    parts.extend(converted.to_ndarray().tobytes() for converted in resampler.resample(None))
output = Path(__file__).parent / "tests" / "fixtures" / "jfk-16k.wav"
output.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(output), "wb") as audio:
    audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000)
    audio.writeframes(b"".join(parts))
metadata = {"source": URL, "source_sha256": hashlib.sha256(source).hexdigest(),
            "wav_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "format": "16kHz mono PCM16 WAV", "source_project": "https://github.com/openai/whisper", "source_license": "MIT"}
(output.parent / "source.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata))
