# be18-specguard-backend/python-server/Dockerfile
FROM python:3.13.7-slim

WORKDIR /app

# Poetry 설치
RUN pip install --no-cache-dir poetry

# Poetry 설정
ENV POETRY_VIRTUALENVS_CREATE=false \
    POETRY_NO_INTERACTION=1

# 의존성 설치 (빌드 캐시 최적화)
COPY pyproject.toml poetry.lock ./
RUN python --version
RUN poetry run python --version

RUN poetry install --no-root --only main
# 실제 코드 복사
COPY . .

# FastAPI 실행
CMD ["poetry", "run", "python", "run.py"]
