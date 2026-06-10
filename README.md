# ai-poc — 공부 계획 생성기

AI provider 호출이 실제로 동작하는지 확인하기 위한 PoC입니다.  
완성도보다 **동작 확인**이 목적이므로 입력값은 `main.py`에 하드코딩되어 있습니다.

---

## 실행 방법

### 1. 의존성 설치
```bash
pip install -r requirements.txt
```

### 2. 환경변수 설정
```bash
cp .env.example .env
# .env 파일을 열어 API 키와 PROVIDER 값을 채운다
# .env 파일을 열어 OPENROUTER_MODEL에다가 openrouter/free -> 기입
```

### 3. 실행
```bash
python main.py
```

---

## Provider 교체 방법

`.env`에서  2줄만 바꾸면 됩니다.

```dotenv
PROVIDER=grok     # 현재
PROVIDER=gemini   # Gemini로 교체
PROVIDER=claude   # Claude로 교체
```

OPENROUTER_MODEL -> 이건 OPENROUTER에서 여러 모델 사용하기 위한 변수 / 다른 에이전트 모델 사용하거면 지워용!

새 provider를 추가하려면 `providers.py`에서 `BaseProvider`를 상속한 클래스를 만들고  
`_REGISTRY`에 등록하면 됩니다. `main.py`는 수정할 필요가 없습니다.

---

## 출력 예시

```
[PoC] provider=openrouter 로 호출합니다...

{
  "subject": "운영체제",
  "total_days": 5,
  "daily_plans": [
    {
      "day": 1,
      "date_offset": "D-5",
      "topics": ["프로세스 관리"],
      "goal": "프로세스 생명주기와 상태 전이 이해하기",
      "estimated_hours": 3
    },
    {
      "day": 2,
      "date_offset": "D-4",
      "topics": ["메모리 관리"],
      "goal": "페이징과 세그멘테이션 개념 정리",
      "estimated_hours": 3
    },
    {
      "day": 3,
      "date_offset": "D-3",
      "topics": ["파일 시스템"],
      "goal": "inode 구조와 디렉토리 관리 파악",
      "estimated_hours": 2
    },
    {
      "day": 4,
      "date_offset": "D-2",
      "topics": ["동기화 문제"],
      "goal": "뮤텍스·세마포어·데드락 조건 암기",
      "estimated_hours": 3
    },
    {
      "day": 5,
      "date_offset": "D-1",
      "topics": ["프로세스 관리", "메모리 관리", "파일 시스템", "동기화 문제"],
      "goal": "전 범위 빠르게 복습 + 예상 문제 풀기",
      "estimated_hours": 4
    }
  ],
  "tips": [
    "그림으로 개념을 정리하면 암기 효율이 올라갑니다",
    "동기화 문제는 반드시 코드 레벨로 이해하세요"
  ]
}
```

---

## 다음 작업

- [ ] 응답을 toon 형식으로 변환
- [ ] 입력값을 CLI 인자로 받기 (`argparse`)
