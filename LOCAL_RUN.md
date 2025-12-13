# Local reproduction of the CI translation job

Use this when you want to mirror the GitHub Actions job locally. Replace the example repos with your own; only DRY_RUN is flipped to stay safe.

## Prerequisites
- Java 21 available (SDKMAN! Temurin 21 works).
- `.env` populated with tokens/keys. For this recipe make sure:
  - `LLM_PROVIDER=gemini`
  - `LLM_MODEL=gemini-2.5-flash`
  - `UPSTREAM_URL=https://github.com/example/docs-upstream.git`
  - `ORIGIN_URL=https://github.com/example/docs-ja.git` (or your fork if you prefer)
  - `ORIGIN_BRANCH=main`
  - `TRANSLATION_BRANCH_TEMPLATE=sync-<upstream-short-sha>` (avoid wrapping in quotes)
  - `MAX_FILES_PER_RUN=0`
  - `TRANSLATION_TARGET_SHA=` (empty)

## Dry-run command (CI parity)
This matches the CI job except it disables pushes/PRs. The `--args` block must stay as one quoted string. Replace the URLs with your upstream/origin.

```bash
set -a && source .env && set +a && \
DRY_RUN=true LOG_FORMAT=json MAX_FILES_PER_RUN=0 TRANSLATION_TARGET_SHA= \
./gradlew :app:run --stacktrace --info --args="--upstream-url https://github.com/example/docs-upstream.git --origin-url https://github.com/example/docs-ja.git --origin-branch main --translation-branch-template sync-<upstream-short-sha> --mode batch --log-format json --translation-mode production" | tee /tmp/local-run.log
```

What to expect:
- Creates a translation branch like `sync-3485844` (no push in dry-run).
- Translates all pending docs (limit is unlimited because `MAX_FILES_PER_RUN=0`).
- Retries are automatic on HTTP 429; you will see retry logs.
- Conflict markers in non-doc files stay as-is in dry-run; note them before a real run.

## Switch to a real run
- After reviewing `/tmp/local-run.log`, rerun with `DRY_RUN=false` (keep the other env values the same) to let it create and push the branch/PR.
- If you prefer your fork as origin, change `ORIGIN_URL` and set a token with push rights.

## Troubleshooting tips
- If you get quoting errors, ensure the entire `--args` payload is inside one set of double quotes.
- Do not single-quote `TRANSLATION_BRANCH_TEMPLATE`; use `sync-<upstream-short-sha>` as-is.
- To target a specific upstream commit, set `TRANSLATION_TARGET_SHA` to the short SHA and rerun.
