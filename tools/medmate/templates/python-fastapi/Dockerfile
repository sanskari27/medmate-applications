FROM python:3.13-slim AS dev
WORKDIR /app
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-dev.txt
COPY . .
ENV PYTHONPATH=/app

FROM dev AS format
RUN ruff format --check app tests

FROM dev AS lint
RUN ruff check app tests

FROM dev AS typecheck
RUN mypy app

FROM dev AS unit
RUN pytest tests/test_message.py

FROM dev AS integration
RUN pytest tests/test_message_integration.py

FROM dev AS build
RUN python -m compileall -q app

FROM python:3.13-slim AS runtime
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
ENV PORT=__PORT__
EXPOSE __PORT__
CMD ["sh", "-c", "python -m uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
