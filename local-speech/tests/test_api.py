"""Real HTTP endpoints with the actual eSpeak executable. No model substitutes."""
import io
import json
import os
import socket
import subprocess
import sys
import time
import tempfile
import urllib.error
import urllib.request
import wave
from pathlib import Path
import pytest


def request(url, body=None, content_type=None):
    headers = {"Content-Type": content_type} if content_type else {}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers), timeout=90) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as response:
        return response.code, response.headers, response.read()


def wav(seconds=0.1):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as audio:
        audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000)
        audio.writeframes(b"\0\0" * int(seconds * 16000))
    return buffer.getvalue()


def upload(base, audio, model="whisper-1"):
    boundary = "test-speech-boundary"
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="model"\r\n\r\n{model}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="../untrusted.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode()
    data += audio + f"\r\n--{boundary}--\r\n".encode()
    return request(base + "/v1/audio/transcriptions", data, f"multipart/form-data; boundary={boundary}")


@pytest.fixture(scope="module")
def api():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0)); port = listener.getsockname()[1]
    env = dict(os.environ, WHISPER_PRELOAD="false")
    process = subprocess.Popen([sys.executable, "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", str(port), "--no-access-log"], env=env)
    base = f"http://127.0.0.1:{port}"
    try:
        for _ in range(100):
            if process.poll() is not None:
                pytest.fail("Speech API failed to start")
            try:
                request(base + "/health"); break
            except OSError:
                time.sleep(0.1)
        else:
            pytest.fail("Speech API startup timeout")
        yield base
    finally:
        process.terminate()
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired: process.kill(); process.wait()


def test_health_distinguishes_unloaded_asr_from_real_tts(api):
    status, _, body = request(api + "/health")
    health = json.loads(body)
    assert status == 503
    assert health["ready"] is False
    assert health["asr"]["status"] == "not_loaded"
    assert health["tts"]["ready"] is True


def test_real_espeak_chinese_synthesis_returns_wav(api):
    status, headers, body = request(api + "/v1/audio/speech", json.dumps({"input": "你好，这是本地语音测试。", "voice": "alloy", "response_format": "wav"}).encode(), "application/json")
    assert status == 200
    assert headers.get_content_type() == "audio/wav"
    with wave.open(io.BytesIO(body)) as audio:
        assert audio.getnframes() > 1000
        assert audio.getnchannels() == 1


@pytest.mark.parametrize("payload", [{"input": "字" * 601}, {"input": "   "}, {"input": "你好", "voice": "--help"}, {"input": "你好", "response_format": "mp3"}])
def test_invalid_synthesis_is_rejected(api, payload):
    status, _, body = request(api + "/v1/audio/speech", json.dumps(payload).encode(), "application/json")
    assert status in (400, 422)
    assert b"/tmp/" not in body


def test_unloaded_asr_returns_unavailable_without_fake_text(api):
    status, _, body = upload(api, wav())
    assert status == 503
    assert "text" not in json.loads(body)


@pytest.mark.parametrize("audio", [b"not audio", wav(30.1), b"x" * (5 * 1024 * 1024 + 1)])
def test_asr_rejects_invalid_or_oversized_audio_before_model(api, audio):
    status, _, body = upload(api, audio)
    assert status in (400, 413, 422)
    assert b"/tmp/" not in body


def test_asr_rejects_unapproved_model(api):
    status, _, _ = upload(api, wav(), "../../download-another-model")
    assert status == 400


@pytest.mark.model
def test_real_downloaded_whisper_recognizes_official_human_speech():
    base = os.environ["SPEECH_TEST_URL"]
    status, _, health = request(base + "/health")
    assert status == 200 and json.loads(health)["ready"]
    sample = Path(__file__).parent / "fixtures" / "jfk-16k.wav"
    assert sample.is_file(), "Run python prepare_sample.py before the real-model test"
    status, _, body = upload(base, sample.read_bytes())
    assert status == 200, body
    text = json.loads(body)["text"].lower()
    # The same clear phrases used by the source project's test_transcribe.py.
    assert "my fellow americans" in text, text
    assert "your country" in text, text
    assert "do for you" in text, text

@pytest.mark.model
def test_real_asr_concurrency_rejects_excess_work():
    from concurrent.futures import ThreadPoolExecutor
    import threading
    base = os.environ["SPEECH_TEST_URL"]
    barrier = threading.Barrier(4)
    audio = wav(30)
    def invoke():
        barrier.wait(timeout=5)
        return upload(base, audio)[0]
    with ThreadPoolExecutor(max_workers=4) as workers:
        statuses = list(workers.map(lambda _: invoke(), range(4)))
    assert 200 in statuses, statuses
    assert 429 in statuses, statuses
    assert set(statuses) <= {200, 429}, statuses
