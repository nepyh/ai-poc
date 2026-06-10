"""
providers.py — AI provider 추상화 레이어

새 provider 추가 방법:
  1. BaseProvider를 상속한 클래스 작성
  2. get_provider() 딕셔너리에 등록
  → main.py 는 건드릴 필요 없음
"""

import os
from abc import ABC, abstractmethod


class BaseProvider(ABC):
    """모든 provider가 구현해야 하는 인터페이스"""

    @abstractmethod
    def generate(self, prompt: str) -> str:
        ...


# ── OpenRouter (현재 사용) ──────────────────────────────────────────

class OpenRouterProvider(BaseProvider):
    def __init__(self):
        api_key = os.getenv("OPENROUTER_API_KEY")
        if not api_key:
            raise ValueError("OPENROUTER_API_KEY가 .env에 없습니다")

        # xAI는 OpenAI 호환 엔드포인트 제공
        from openai import OpenAI
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://openrouter.ai/api/v1",
        )
        self.model = os.getenv("OPENROUTER_MODEL", "openrouter/free")

    def generate(self, prompt: str) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content


# ── Gemini (교체 예비) ─────────────────────────────────────────

class GeminiProvider(BaseProvider):
    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY가 .env에 없습니다")
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel(
            os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
        )

    def generate(self, prompt: str) -> str:
        response = self.model.generate_content(prompt)
        return response.text


# ── Claude (교체 예비) ─────────────────────────────────────────

class ClaudeProvider(BaseProvider):
    def __init__(self):
        api_key = os.getenv("ANTHROPIC_API_KEY")
        if not api_key:
            raise ValueError("ANTHROPIC_API_KEY가 .env에 없습니다")
        import anthropic
        self.client = anthropic.Anthropic(api_key=api_key)
        self.model = os.getenv("CLAUDE_MODEL", "claude-haiku-4-5-20251001")

    def generate(self, prompt: str) -> str:
        message = self.client.messages.create(
            model=self.model,
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )
        return message.content[0].text


# ── 레지스트리 ────────────────────────────────────────────────

_REGISTRY = {
    "openrouter": OpenRouterProvider,
    "gemini": GeminiProvider,
    "claude": ClaudeProvider,
}


def get_provider(name: str) -> BaseProvider:
    cls = _REGISTRY.get(name.lower())
    if cls is None:
        raise ValueError(f"알 수 없는 provider: {name!r}. 가능한 값: {list(_REGISTRY)}")
    return cls()
