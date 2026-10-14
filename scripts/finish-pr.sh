#!/usr/bin/env bash
# پایانِ کارِ یک PR: ادغام (اگر CI سبز است)، همگام‌سازیِ چک‌اوتِ اصلی، پاک‌کردنِ worktree و شاخه‌ها، و compile.
#
#   scripts/finish-pr.sh <شماره‌ی PR> [--dry-run] [--no-merge] [--no-compile]
#
# هر مرحله شرطِ ایمنی دارد و در صورتِ شک متوقف می‌شود، نه حذف می‌کند:
#   - ادغام فقط وقتی PR باز است، قابل ادغام است و همه‌ی checkها پاس شده‌اند (--no-merge: ادغام را نمی‌کند و
#     فقط اگر PR از قبل MERGED باشد ادامه می‌دهد)؛
#   - worktree و شاخه‌ی محلی فقط وقتی پاک می‌شوند که تغییرِ کامیت‌نشده نداشته باشند و کامیتِ سرِ شاخه
#     (یا همان کامیتی که GitHub برای PR ثبت کرده) در origin/<شاخه‌ی پایه> باشد؛
#   - چک‌اوتِ اصلی فقط با --ff-only و فقط اگر روی شاخه‌ی پایه و تمیز باشد جلو می‌رود؛
#   - compile (mvn -o compile) قالب‌های JTE و مهاجرت‌های تازه را وارد بیلدِ چک‌اوتِ اصلی می‌کند تا
#     «Circular view path» ی بیلدِ کهنه پیش نیاید.
# از هر جایی (حتی از داخلِ خودِ worktree) صدا زده می‌شود؛ پاک‌کردنِ worktree همیشه از چک‌اوتِ اصلی انجام می‌شود.
# حذفِ worktreeی که نشستِ جاری در آن است، نشست را بی‌پوشه می‌کند؛ پس این آخرین کارِ نشست باشد.
set -euo pipefail

usage() { echo "usage: $0 <pr-number> [--dry-run] [--no-merge] [--no-compile]" >&2; exit 2; }

PR=""
DRY=0
MERGE=1
COMPILE=1
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1 ;;
    --no-merge) MERGE=0 ;;
    --no-compile) COMPILE=0 ;;
    -*) usage ;;
    *) [ -z "$PR" ] && PR="$arg" || usage ;;
  esac
done
[ -n "$PR" ] || usage
[[ "$PR" =~ ^[0-9]+$ ]] || usage

say() { echo "• $*"; }
run() { if [ "$DRY" = 1 ]; then echo "  [dry-run] $*"; else "$@"; fi; }
stop() { echo "✗ $*" >&2; exit 1; }

command -v gh >/dev/null || stop "gh لازم است"

# چک‌اوتِ اصلی = پوشه‌ی مشترکِ همه‌ی worktreeها
COMMON="$(git rev-parse --path-format=absolute --git-common-dir)"
MAIN="$(dirname "$COMMON")"
cd "$MAIN"

read -r STATE BRANCH BASE HEAD_SHA MERGEABLE < <(
  gh pr view "$PR" --json state,headRefName,baseRefName,headRefOid,mergeable \
    --jq '[.state,.headRefName,.baseRefName,.headRefOid,.mergeable] | @tsv' | tr '\t' ' ')
say "PR #$PR: $STATE، شاخه $BRANCH ← $BASE"

if [ "$STATE" = "OPEN" ]; then
  [ "$MERGE" = 1 ] || stop "PR باز است و --no-merge داده شده"
  [ "$MERGEABLE" = "MERGEABLE" ] || stop "PR قابل ادغام نیست ($MERGEABLE)"
  CHECKS="$(gh pr checks "$PR" 2>&1 || true)"
  if echo "$CHECKS" | grep -qiE "fail|pending|in_progress|queued"; then
    echo "$CHECKS" >&2
    stop "همه‌ی checkها هنوز پاس نشده‌اند؛ ادغام نمی‌کنم"
  fi
  say "ادغام (merge commit)"
  run gh pr merge "$PR" --merge
  [ "$DRY" = 1 ] || STATE="$(gh pr view "$PR" --json state --jq .state)"
  [ "$DRY" = 1 ] || [ "$STATE" = "MERGED" ] || stop "ادغام انجام نشد (حالت: $STATE)"
elif [ "$STATE" != "MERGED" ]; then
  stop "PR در حالت $STATE است؛ چیزی پاک نمی‌کنم"
fi

git fetch -q origin

# --- همگام‌سازیِ چک‌اوتِ اصلی ---
CURRENT="$(git branch --show-current)"
if [ "$CURRENT" = "$BASE" ] && [ -z "$(git status --porcelain)" ]; then
  say "pull --ff-only روی $BASE"
  run git pull -q --ff-only origin "$BASE"
else
  say "چک‌اوتِ اصلی روی '$CURRENT' است یا تغییرِ کامیت‌نشده دارد؛ pull نمی‌کنم"
fi

# --- worktree و شاخه‌ی محلی ---
WT="$(git worktree list --porcelain | awk -v b="refs/heads/$BRANCH" '
  /^worktree /{p=$2} /^branch /{if($2==b) print p}')"
if [ -n "$WT" ] && [ "$WT" != "$MAIN" ]; then
  [ -z "$(git -C "$WT" status --porcelain)" ] || stop "worktree $WT تغییرِ کامیت‌نشده دارد؛ پاک نمی‌کنم"
  TIP="$(git -C "$WT" rev-parse HEAD)"
  if ! git merge-base --is-ancestor "$TIP" "origin/$BASE" && [ "$TIP" != "$HEAD_SHA" ]; then
    stop "کامیتِ سرِ $BRANCH در origin/$BASE نیست و با سرِ PR هم نمی‌خواند؛ پاک نمی‌کنم"
  fi
  say "حذفِ worktree $WT"
  run git worktree remove "$WT"
elif [ "$WT" = "$MAIN" ]; then
  say "شاخه‌ی $BRANCH در چک‌اوتِ اصلی است؛ worktree ای برای حذف نیست"
fi

if git show-ref --verify --quiet "refs/heads/$BRANCH" && [ "$BRANCH" != "$(git branch --show-current)" ]; then
  TIP="$(git rev-parse "refs/heads/$BRANCH")"
  if git merge-base --is-ancestor "$TIP" "origin/$BASE" || [ "$TIP" = "$HEAD_SHA" ]; then
    say "حذفِ شاخه‌ی محلی $BRANCH"
    run git branch -D "$BRANCH"
  else
    echo "• شاخه‌ی محلیِ $BRANCH کامیتِ ادغام‌نشده دارد؛ نگه داشته شد" >&2
  fi
fi

if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
  say "حذفِ شاخه‌ی ریموت $BRANCH"
  run git push -q origin --delete "$BRANCH"
fi
run git fetch -q --prune origin

# --- compile ---
if [ "$COMPILE" = 1 ] && [ "$(git branch --show-current)" = "$BASE" ]; then
  say "mvn -o compile (JTE و مهاجرت‌های تازه)"
  run ./mvnw -q -o compile
fi
say "تمام شد"
