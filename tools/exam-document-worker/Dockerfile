FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt /opt/exam/requirements.txt
RUN pip install --no-cache-dir -r /opt/exam/requirements.txt
COPY worker.py /opt/exam/worker.py
USER 65534:65534
WORKDIR /tmp
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "/opt/exam/worker.py"]
