FROM python:3.11-slim AS runtime
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 HF_HUB_DISABLE_TELEMETRY=1 WHISPER_MODEL=tiny WHISPER_DOWNLOAD_ROOT=/models HF_HUB_DISABLE_XET=1
RUN apt-get update && apt-get install -y --no-install-recommends espeak-ng libgomp1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN mkdir -p /models /tmp/local-speech
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=300s --retries=6 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)"
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log"]

FROM runtime AS test
RUN pip install --no-cache-dir -r requirements-test.txt
CMD ["pytest", "-q", "tests", "-m", "not model"]

FROM runtime AS final
