# ai-docsite-translator

## GitHub Actions での利用
```
jobs:
  translate:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v4
      - uses: <owner>/ai-docsite-translator@v1
        with:
          upstream-url: https://github.com/example/upstream.git
          origin-url: https://github.com/example/origin.git
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          LLM_PROVIDER: gemini
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
```

### 主な入力
- `upstream-url` (必須) — 翻訳元リポジトリの URL。
- `origin-url` (必須) — 翻訳先リポジトリの URL。
- `origin-branch` (任意, 既定値 `main`).
- `translation-branch-template` (任意, 既定値 `sync-<upstream-short-sha>`).
- `dry-run` (任意, `true` でコミットや PR をスキップ)。
- `extra-args` (任意, CLI へそのまま渡す追加引数)。

Mode や翻訳モードなどの詳細オプションは必要に応じ `extra-args` で指定できます。Secrets / 環境変数 (`GITHUB_TOKEN`, `LLM_PROVIDER`, `LLM_MODEL`, `GEMINI_API_KEY`, `OLLAMA_BASE_URL` など) はワークフロー側で設定してアクションへ渡してください。
