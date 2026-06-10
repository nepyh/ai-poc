"""
AI PoC — 공부 계획 생성기
목적: AI provider를 실제로 호출할 수 있는지 확인 (완성도보다 동작 확인)
"""

import os
import json
from dotenv import load_dotenv
from providers import get_provider

load_dotenv()

# ── 입력값 (나중에 인자로 받을 예정) ──────────────────────────
INPUT = {
    "subject": "운영체제",
    "days_remaining": 5,
    "scope": ["프로세스 관리", "메모리 관리", "파일 시스템", "동기화 문제"],
}
# ─────────────────────────────────────────────────────────────

PROMPT = f"""
당신은 학습 계획 전문가입니다.
다음 정보를 바탕으로 {INPUT['days_remaining']}일 공부 계획을 만들어주세요.

과목: {INPUT['subject']}
남은 기간: {INPUT['days_remaining']}일
시험 범위: {", ".join(INPUT['scope'])}

반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.
반드시 모든 응답은 한국어로 작성하세요.
중국어, 일본어, 영어를 사용하지 마세요.

{{
  "subject": "과목명",
  "total_days": 숫자,
  "daily_plans": [
    {{
      "day": 1,
      "date_offset": "D-5",
      "topics": ["주제1", "주제2"],
      "goal": "오늘의 목표 한 줄",
      "estimated_hours": 숫자
    }}
  ],
  "tips": ["팁1", "팁2"]
}}
"""


def main():
    provider_name = os.getenv("PROVIDER", "openrouter")
    print(f"[PoC] provider={provider_name} 로 호출합니다...\n")

    provider = get_provider(provider_name)
    raw = provider.generate(PROMPT)

    # JSON 파싱
    try:
        plan = json.loads(raw)
    except json.JSONDecodeError:
        # 모델이 ```json ... ``` 감싸서 줄 때 대응
        import re
        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if not match:
            print("[오류] JSON을 찾지 못했습니다. 원본 응답:")
            print(raw)
            return
        plan = json.loads(match.group())

    print(json.dumps(plan, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
