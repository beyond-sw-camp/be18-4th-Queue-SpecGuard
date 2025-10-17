# ===============================
# Base Image
# ===============================
FROM python:3.13-slim

# 작업 디렉터리
WORKDIR /app

# 필수 패키지 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    python3-dev \
    libffi-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Poetry 설치
RUN pip install --upgrade pip
RUN pip install poetry

# 의존성 파일 복사
COPY pyproject.toml poetry.lock ./

# 의존성 설치 (main only, no root)
RUN poetry install --only main --no-root
#
# 소스 복사
COPY . .

# 포트 설정 (Flask/FastAPI 등 기본 8000 가정)
EXPOSE 8000

# FastAPI 실행
CMD ["poetry", "run", "python", "run.py"]
