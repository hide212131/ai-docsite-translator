# AI翻訳スクリプト 仕様書 (SPEC)

## 1. 概要
本仕様書は、英語原文のドキュメントサイト（upstream）と日本語翻訳サイト（origin）の差分を検出し、LangChain4j＋Gemini を利用して翻訳し、日本語サイト向けの Pull Request を自動作成するスクリプト（以下「スクリプト」）の詳細仕様を定義する。GitHub Actions での定期実行およびローカル開発環境での検証を両立させる。

## 2. スコープ
- upstream と origin の差分同期と翻訳処理
- 翻訳結果のコミットおよび PR 作成の自動化
- GitHub Actions とローカル開発モードの双方で動作する実行スクリプト群

## 3. 用語定義
- upstream: 原文（英語）ドキュメントを保持する GitHub リポジトリ。
- origin: 日本語翻訳ドキュメントを保持する GitHub リポジトリ。
- 翻訳ブランチ: スクリプトが origin 上に作成する翻訳結果用の作業ブランチ。

## 4. 前提条件と制約
- upstream と origin のディレクトリ／ファイル構成は完全一致させる。
- 翻訳ファイル内の行数は原文と一致させ、改行位置も揃える。
- 文書ファイル（.md, .mdx, .txt, .html）のみ翻訳対象とする。
- ビルドは Gradle の最新安定版を使用する。
- Java 最新 LTS（例: Java 21）を使用し、LangChain4j v1.5.0 以上を採用する。
- LangChain4j Agents チュートリアル（https://docs.langchain4j.dev/tutorials/agents）に準拠した Agentic アーキテクチャを採用する。
- LLM は Gemini（API キーは GitHub Actions Secrets に格納）を使用する。
- ネットワークアクセスや Secrets への参照は、GitHub Actions 上でのみ許可される前提とする。

## 5. 全体アーキテクチャ
- Java ベースの CLI ツールとして実装。
- モジュール構成を以下のサブパッケージに分割し、役割ごとに責務を分離する。
  - `config`: 設定値、環境変数、Secrets 読み込み。
  - `git`: upstream / origin のクローン、フェッチ、ブランチ操作、差分検出。
  - `diff`: 未翻訳ファイル特定、差分解析、行数調整（改行のみ判定と挿入／削除ユーティリティ含む）。
  - `agent`: LangChain4j Agents を用いたタスクオーケストレーションとツール定義。
  - `translate`: LangChain4j/Gemini を呼び出し、文書翻訳と整形を行う。
  - `writer`: 翻訳結果の書き込み、行数補正、ファイル保存。
  - `pr`: PR 作成、本文生成、リンク挿入。
  - `cli`: エントリーポイント、コマンドライン引数解析、モード制御。

## 6. ワークフロー
### 6.1 GitHub Actions 実行フロー
1. スクリプトリポジトリを checkout。
2. upstream と origin を指定 URL からクローン。
3. スクリプトを `--mode batch` で実行。
4. 未翻訳差分を抽出し翻訳。
5. 翻訳ブランチにコミット、PR を作成。
6. Actions ログに処理結果サマリを出力。

### 6.2 ローカル開発モード
- `--mode dev --since <commit>` で指定コミット以前の未翻訳差分のみ翻訳可能にする。
- ローカル環境で upstream / origin の代替パスを指定できるよう CLI オプションを設ける。
- Actions 依存の処理（Secrets 読込、PR 作成等）はモックまたは dry-run とする切替機能を提供。

### 6.3 翻訳ブランチ運用フロー
1. origin の `main` をベースに、`sync-<upstream-short-sha>` 形式の翻訳ブランチを作成する。`<upstream-short-sha>` は翻訳対象となる upstream/main の最新コミットの短縮ハッシュを用いる。
2. upstream/main のマージ履歴を解析し、origin 側で未翻訳のコミットを特定する。Git のツリーとマージコミットをたどり、dev モードでは CLI から任意コミットを明示指定できるようにする。
3. 翻訳ブランチに upstream/main を強制的にマージし、コンフリクトが発生してもマージコミットを生成する。コンフリクトマーカーは翻訳対象箇所の抽出に利用する。
4. マージ後に更新されたファイルを分類し、翻訳対象と非対象を分離する。
   - a. 文書ファイルの新規作成: 全文を英語原文から翻訳する。
   - b. 既存文書ファイルの変更: 新規英文追加（b-1）と翻訳済み領域の更新（b-2）を区別し、必要に応じて差分のみ翻訳または再翻訳する。
   - c. 文書ファイル以外（設定ファイル等）: 翻訳対象外として現状維持する。

## 7. 機能要件
### 7.1 入力
- CLI オプション
  - `--upstream-url`, `--origin-url`
  - `--origin-branch` (デフォルト: `main`)
  - `--translation-branch-template` (デフォルト: `sync-<upstream-short-sha>`)
  - `--mode` (`batch` | `dev`)
  - `--since <commit>`（dev モード時のみ有効）
  - `--dry-run`（PR/Push を抑止）
  - `--translation-mode` (`production` | `dry-run` | `mock`) — 翻訳パイプラインの実行モード切替
- Secrets / 環境変数
  - `GEMINI_API_KEY`
  - GitHub Token (`GITHUB_TOKEN`) for PR creation
  - `TRANSLATION_TARGET_SHA`（任意、dev/テスト向けに短縮コミットハッシュを指定）
  - `TRANSLATION_MODE`（任意、CLI 未指定時の翻訳モード既定値）

※ テンプレート内の `<upstream-short-sha>` は、翻訳対象となる最新 upstream コミットの短縮ハッシュ（7 文字想定）に置換する。

### 7.2 処理手順
1. 設定読込: CLI 引数と環境変数を統合し設定オブジェクト生成。
2. Agent 初期化: LangChain4j Agents チュートリアル準拠で、`@Agent` を付与した翻訳オーケストレーターインターフェースを定義し、`AgenticServices.agentBuilder` で `DiffTool` / `TranslationTool` / `LineStructureAdjusterTool` / `PullRequestTool` 等を束ねたエージェントを生成する。
3. リポジトリ同期: upstream/origin の最新をフェッチし、origin の `main` から `sync-<upstream-short-sha>` 形式（オプションで変更可能）の翻訳ブランチを作成する。
4. 未翻訳コミット解析: upstream/main のマージ履歴を走査して origin 未反映コミットを抽出し、対象コミットの短縮ハッシュをブランチ名・PR 情報に反映する。dev モードでは `--since` や明示したコミット指定を優先し、`TRANSLATION_TARGET_SHA` が設定されている場合はその短縮ハッシュを優先ターゲットとして処理する。
5. upstream マージとファイル分類: 翻訳ブランチへ upstream/main を強制マージし、コンフリクトが生じてもマージコミットを生成する。マージ後の更新ファイルを a/b/c 区分（新規文書、既存文書の追記・再翻訳、非文書）に分類し、文書ファイルのみ差分解析キューへ投入する。
6. 自動コンフリクト解消: 文書ファイルにコンフリクトマーカーが残っている場合でも、以下の手順で既存訳を最大限活用して解消する。
   - `ConflictDetector` が `<<<<<<<`/`=======`/`>>>>>>>` を検出し、基底訳（origin/main の既存日本語）、旧英語、最新英語の 3-way セグメントを抽出する。
   - `ConflictResolutionPlanner` が upstream 側の差分（変更/追加/削除）を特定し、影響範囲を最小限に絞った翻訳サブタスクを生成する。既存訳に一致する行は優先的に保持し、差分に対してのみ再翻訳を要求する。
   - 再翻訳結果を基底訳にパッチ適用した後、構文不整合がないか `LineStructureAnalyzer` で検証し、保持不能な衝突が残れば対象ファイルを「要手動確認」としてスキップ・レポートする。
   - コンフリクト解消成功時はマーカーを除去したクリーンなファイルを次段へ渡す。
7. 追加行の翻訳キュー投入: upstream 側で新規に追加された行で既存訳に対応する行が存在しない場合、`DiffAnalyzer` が新規追加領域を検知し、`TranslationTaskPlanner` が該当行を追加セグメントとして翻訳タスクへ組み込む。既存訳がない領域は常に翻訳対象とし、コンフリクトの有無に依らず差分を埋める。
8. 行数補正: 原文と翻訳ファイルの行数が異なる場合、`LineStructureAnalyzer` が空行／ホワイトスペース／コンテンツ行の連続区間を抽出し、`LineStructureAdjuster` がそのメタデータを基に不足行を補完して行数を一致させる。将来的に LLM ベースの調整へ拡張できるよう、Analyzer→Adjuster の責務を分離する。
9. 翻訳処理:
   - `TranslatorFactory` が `TranslationMode` に応じてプロダクション／ドライラン／モックの訳出パスを選択し、LangChain4j/Gemini 実装を差し替え可能にする。
   - `TranslationTask` 単位で翻訳セグメントを生成し、`LineStructureFormatter` で行構造を整えた上で既存訳へマージする。
   - 翻訳済み部分とのマージ方針: 新規差分のみ追記または必要箇所の再翻訳。
   - 原文の軽微修正（誤字等）と判断した差分はスキップ可能とする判定ロジックを実装。
10. ファイル書き込み: 翻訳結果をorigin側ディレクトリに保存、改行コード・エンコーディングを統一。
11. コミット: 翻訳ファイル一覧をメッセージに含めてコミット。
12. PR 作成: PR タイトルと本文を生成し、原文コミットと翻訳コミットのリンクを掲載。

### 7.3 出力
- 翻訳済みファイル更新
- origin に作成された翻訳ブランチ
- PR (dry-run 時は PR 本文ドラフトを標準出力へ)
- ログ: 処理結果の詳細、翻訳対象ファイル一覧、エラー情報

## 8. 非機能要件
- 冪等性: 同じ upstream 状態で再実行しても差分がない場合は何もコミットしない。
- 拡張性: 対応ファイル拡張子を設定で追加可能にする。
- 信頼性: 行数不一致や翻訳 API エラー発生時に処理を停止し、詳細ログを出力。
- パフォーマンス: 大量ファイルでも翻訳対象を差分に限定し、並列翻訳は設定で制御。
- ロギング: INFO レベルで主要ステップ、DEBUG レベルで API リクエストとレスポンス要約を記録。

## 9. データモデル / 主なクラス
- `AppConfig`: CLI 引数・環境変数から生成される設定値。
- `RepositoryManager`: upstream / origin の git 操作をラップ。
- `DiffAnalyzer`: 差分抽出、ファイル状態判定。
- `ConflictDetector` / `ConflictResolutionPlanner`: マージコンフリクトを検出し、既存訳をベースに差分箇所のみを再翻訳するパッチプランを生成（コンフリクト以外の追加行は `DiffAnalyzer`/`TranslationTaskPlanner` が補完）。
- `LineAligner`: `LineStructureAnalyzer`（改行状況の計測）と `LineStructureAdjuster`（改行挿入・削除の実行）を提供し、Analyzer の結果を LLM に渡して Adjuster パラメータを導出する。
- `TranslationService` / `TranslatorFactory` / `Translator`: LangChain4j/Gemini をラップし、モードごとの翻訳処理と行構造整形 (`LineStructureFormatter`) を担う。
- `TranslationTask` / `TranslationSegment`: 翻訳対象ドキュメントと差分セグメントを表現し、既存訳へのマージと差分限定翻訳を制御。
- `CommitService`: コミット/ブランチ操作。
- `PullRequestService`: PR 作成 API 呼び出し。
- `Logger`: 標準ログ仕組み（SLF4J 等）で記録。

## 10. 外部インターフェース
- GitHub REST API / GraphQL API (PR 作成、リポジトリ操作) — `GITHUB_TOKEN`
- Gemini API (LangChain4j 経由)
- ローカルファイルシステム（翻訳ファイル出力）

## 11. 設定と Secrets
| 項目 | 説明 | 必須 | 既定値 |
| --- | --- | --- | --- |
| `GEMINI_API_KEY` | Gemini API キー | 必須 | なし |
| `GITHUB_TOKEN` | PR 作成・push 用 Token | 必須 | なし |
| `TRANSLATION_BRANCH_TEMPLATE` | 翻訳ブランチ名テンプレート | 任意 | `sync-<upstream-short-sha>` |
| `TRANSLATION_FILE_EXTENSIONS` | 翻訳対象拡張子 | 任意 | `.md,.mdx,.txt,.html` |
| `MAX_FILES_PER_RUN` | 1 回の処理で翻訳する最大ファイル数 | 任意 | 無制限 |
| `TRANSLATION_TARGET_SHA` | dev/テスト時に優先する短縮コミットハッシュ | 任意 | なし |

## 12. エラーハンドリング
- Git 操作失敗: リトライ後に致命的エラーとして終了。
- 翻訳 API エラー: エラー内容を記録し、対象ファイルはスキップして処理継続、最後に失敗一覧を出力。
- 行数整合性エラー: 翻訳結果を破棄し、開発者向けに手動介入を促す。
- コンフリクト自動解消失敗: 対象ファイルを保留リストに追加し、既存訳を維持したまま手動解消が必要な旨をログとサマリで通知。
- 追加行検出/翻訳失敗: 未翻訳の英語行を残さないよう対象ファイルを警告付きでサマリ出力し、再実行または手動翻訳を促す。
- PR 作成失敗: 詳細ログを出力し、dry-run モードで再実行できるよう終了コードで通知。

## 13. 開発モード（dev）要求
- `--mode dev` 時は GitHub Actions 依存機能を無効化し、CLI から指定したローカルパスを使用可能とする。
- `--since <commit>` で指定コミット以前の未翻訳差分のみ処理。
- 翻訳対象ファイル数を制限するオプション（例: `--limit 5`）を提供。
- 翻訳結果と PR 本文テンプレートを標準出力へプレビュー。
- `TRANSLATION_TARGET_SHA` を設定すると優先対象コミットを強制指定でき、統合テストや再現テストで複数コミットを切り替えて検証できるようにする。

## 14. ログ出力フォーマット
- JSON 形式とテキスト形式の切替をサポート（デフォルト: テキスト）。
- 主要イベント: リポジトリ同期開始/終了、差分件数、翻訳 API 呼出時間、コミット ID、PR URL。

## 15. テスト戦略
- Unit Test: 各サービスクラス（DiffAnalyzer, LineAligner, Translator ラッパー）を対象。
- Unit Test: `ConflictDetector` / `ConflictResolutionPlanner` で 3-way 差分（追加・削除・編集・複合）および追加行検出が正しく翻訳セグメントへ変換されることを検証。
- Integration Test: モックリポジトリを用いた end-to-end テスト（翻訳 API はモック）。
- Integration Test では `TRANSLATION_TARGET_SHA` や CLI フラグを組み合わせ、任意の短縮コミットを切り替えて翻訳ブランチ作成～コミットまでの動作を自動検証する。
- ローカル自動テストスクリプト: dev モードでサンプル差分を翻訳し、結果を検証するサンプルを提供。
- GitHub Actions CI: ビルド、Unit Test 実行、静的解析（SpotBugs 等）を行うワークフローを用意。

## 16. PR 作成仕様
- 翻訳ブランチ名: `sync-<upstream-short-sha>` の形式（PR タイトルと揃える）。
- PR タイトル: `docs: sync-<upstream-short-sha>` の形式。
- PR 本文テンプレート:
  - 原文コミットリンク (upstream)
  - 翻訳コミットリンク (origin 翻訳ブランチ)
  - 翻訳対象ファイル一覧 (箇条書き)
  - 注意事項（レビュー観点、翻訳方針）
- リンク生成時は GitHub の完全 URL（`https://github.com/<owner>/<repo>/commit/<sha>`）を使用。

## 17. セキュリティ
- Secrets は GitHub Actions 上のみ参照し、ログに出力しない。
- ローカル実行時は `.env` ファイル等の秘密情報読込に対応しつつ、リポジトリにコミットしない運用を明記。
- API 呼び出し時に TLS/HTTPS を使用。

## 18. 今後の拡張検討事項
- DeepL 等他翻訳エンジンとのフェイルオーバー対応。
- 翻訳レビュー支援（差分ハイライト、コメント自動生成）。
- 翻訳品質評価の自動化。
