# ai-docsite-translator

ドキュメントサイトの原文 (upstream) と翻訳サイト (origin) の差分を検出し、LLM を使って翻訳結果のブランチと Pull Request を自動生成する Java 製 CLI / GitHub Action です。LangChain4j を通じて Ollama もしくは Gemini を呼び出せます。

## 動作要件
- Java 21 (Temurin) 以上
- Gradle Wrapper (`./gradlew`) が利用できる環境
- Git と GitHub リポジトリへのアクセス権
- 翻訳に利用する LLM への接続設定（Ollama もしくは Gemini）

## セットアップ手順
1. リポジトリを clone するか、GitHub Actions から `uses:` で呼び出せるようにします。
2. `.env.sample` をコピーして `.env` を作成し、必要な環境変数を設定します。
3. GitHub Secrets（`GITHUB_TOKEN`, `GEMINI_API_KEY` など）をワークフローで参照できるよう登録します。
4. 翻訳対象の upstream / origin リポジトリ URL を指定し、CLI あるいは GitHub Actions から実行します。

```bash
cp .env.sample .env
# 必要な値を編集したら
./gradlew :app:run --args "--upstream-url https://github.com/example/upstream.git \
  --origin-url https://github.com/example/origin.git --mode dev --dry-run --translation-mode mock"
```

## 主な環境変数 / Secrets
CLI の引数で明示した値が優先されます。未指定の場合は以下の環境変数（`.env` から `dotenv` 等で読み込む想定）を参照します。

| キー | 必須 | 既定値 / 例 | 説明 |
| --- | --- | --- | --- |
| `UPSTREAM_URL` | ✓ | - | 原文リポジトリ (upstream) の Git URL |
| `ORIGIN_URL` | ✓ | - | 翻訳先リポジトリ (origin) の Git URL |
| `ORIGIN_BRANCH` |  | `main` | 翻訳ブランチのベースとなる origin ブランチ |
| `TRANSLATION_BRANCH_TEMPLATE` |  | `sync-<upstream-short-sha>` | 翻訳ブランチ名のテンプレート |
| `MODE` |  | `batch` | `batch` (自動運用) / `dev` (ローカル検証) |
| `DRY_RUN` |  | `false` | `true` にするとブランチ push / PR 作成を抑止 |
| `TRANSLATION_MODE` |  | `production` (`dry-run` / `mock`) | 翻訳処理の挙動切り替え |
| `MAX_FILES_PER_RUN` |  | `0` | 1 回で翻訳するファイル数の上限 (0 は無制限) |
| `SINCE` |  | - | dev モード時に対象とする upstream コミット境界 |
| `TRANSLATION_TARGET_SHA` |  | - | 翻訳対象とする upstream の short SHA を固定 |
| `LLM_PROVIDER` |  | `ollama` | `ollama` / `gemini` |
| `LLM_MODEL` |  | `lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0` | 翻訳に利用するモデル名 |
| `OLLAMA_BASE_URL` | Ollama 利用時 | `http://localhost:11434` | Ollama API のベース URL |
| `GEMINI_API_KEY` | Gemini 利用時 | - | Google Gemini API キー |
| `GITHUB_TOKEN` | Dry run 以外 | - | ブランチ push / PR 作成用トークン |
| `TRANSLATION_INCLUDE_PATHS` |  | - | 翻訳対象とするパス（`,` 区切り） |
| `TRANSLATION_DOCUMENT_EXTENSIONS` |  | `md,mdx,txt,html` | 翻訳対象の拡張子リスト |
| `LOG_FORMAT` |  | `text` | `text` / `json` |

## CLI オプション
Picocli ベースの CLI から直接実行できます。環境変数に加えて以下のオプションが利用可能です。

```
./gradlew :app:run --args "--upstream-url <URL> --origin-url <URL> [--mode batch|dev] [--since <ref>] [--dry-run] \
  [--translation-mode production|dry-run|mock] [--limit <N>] [--log-format text|json] \
  [--translation-branch-template <name>] [--origin-branch <branch>]"
```

- `--mode`: `batch` は自動運用向け、`dev` はローカル検証向けに差分を絞り込みます。
- `--dry-run`: Git への書き込みや PR 作成を抑止。翻訳 API 自体は `translation-mode` に依存します。
- `--translation-mode`: `production` で LLM に投げ、`dry-run` は翻訳せず原文を返却、`mock` はテスト用ダミー出力。
- `--limit`: 1 回の実行で翻訳するファイル数を制限します。
- `--since`: dev モード時に、指定したコミット以降のみを翻訳対象にします。
- `--log-format`: CI 等で機械処理しやすい JSON ログを出力可能です。

## GitHub Actions での利用
`action.yml` を提供しているため、下記のように Reusable Action として呼び出せます。Secrets や環境変数はワークフロー側で設定してください。

```yaml
jobs:
  translate:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    env:
      LLM_PROVIDER: ollama
      LLM_MODEL: lucas2024/hodachi-ezo-humanities-9b-gemma-2-it:q8_0
      OLLAMA_BASE_URL: http://localhost:11434
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
    steps:
      - uses: actions/checkout@v4
      - uses: <owner>/ai-docsite-translator@v1
        with:
          upstream-url: https://github.com/example/upstream.git
          origin-url: https://github.com/example/origin.git
          mode: batch
          dry-run: false
          translation-mode: production
```

## ローカル実行フロー
1. upstream / origin リポジトリをローカルに clone し、`~/.ssh` や HTTPS 認証を設定します。
2. `.env` で `MODE=dev`, `TRANSLATION_MODE=mock`, `DRY_RUN=true` 等を指定して安全にテストします。
3. 実行: `./gradlew :app:run --args "--upstream-url ... --origin-url ... --mode dev --dry-run"`
4. ログ (`build/logs` など) や標準出力で翻訳計画を確認します。
5. 問題がなければ `DRY_RUN=false` と `TRANSLATION_MODE=production` に切り替え、`batch` モードで本番運用に移行します。

## ログと検証
- ログ形式は `LOG_FORMAT=text` (人間向け) と `json` (機械処理向け) を切り替え可能です。
- 翻訳結果コミットや PR URL はログ出力されるため、CI での後処理に利用できます。
- `dry-run` + `mock` を併用すると LLM 呼び出しなしでエンドツーエンドのフローを検証できます。

## 開発者向けメモ
- `./gradlew check` でユニットテストを実行できます。
- 設定値は `ConfigLoader` で統合されるため、新しいパラメータを追加する際は CLI オプションと環境変数の両方を更新してください。
- LLM まわりの実装は `TranslatorFactory` / `ChatModelTranslator` にまとまっています。モデル切り替えロジックを追加する場合はここを参照してください。

この README の内容をもとに、AI エージェントや人間オペレーターが自動翻訳フローをセットアップできるよう設計しています。

