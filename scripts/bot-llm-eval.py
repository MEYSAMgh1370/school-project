#!/usr/bin/env python3
"""Offline evaluation of the Bale bot's free-text router against real providers.

Not run in CI. It sends the same request bodies as the Java adapters
(DecisionModelClient and OpenAiCompatibleClient in notification/internal) and reads the same
prompt files (src/main/resources/bot/llm/instructions.txt and intent-hints.properties),
so the numbers here describe what production would do.

Usage (a model and its adapter are paired by position; repeat both for each model):

  BOT_LLM_BASE_URL=... BOT_LLM_API_KEY=... \\
  python3 scripts/bot-llm-eval.py \\
      --model typesafe/jev-1.13 --adapter decision \\
      --model qwen/qwen3-32b    --adapter openai \\
      [--decision-path /systemone] [--dry-run]

The key is read only from the environment and is never printed. Error lines name only the kind
of failure (timeout, connection, http <status>, unparsable), never a body.

Scoring. An error counts as a wrong answer. "Intent" accuracy compares the chosen intent with
expected_intent. "Intent+target" also compares the chosen label with expected_target, but only on
rows with two labels whose expected intent is not NONE (the only rows where the router asks for a
target). The decision adapter additionally reports accuracy at confidence thresholds: an answer
whose intent confidence is below the threshold counts as NONE (the bot shows the menu); a target
below it counts as NONE target (the bot asks with buttons), as in ClassificationValidator. The openai adapter has
no confidence, so the thresholds do not apply to it.
"""
import argparse
import csv
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LLM_DIR = os.path.join(ROOT, "src", "main", "resources", "bot", "llm")
DEFAULT_SET = os.path.join(ROOT, "scripts", "bot-llm-eval-set.tsv")

# BotIntent order, then NONE (LlmPrompts.intentHints in Java).
INTENT_ORDER = ["ATTENDANCE_TODAY", "ATTENDANCE_SUMMARY", "GRADES", "EXAMS", "TIMETABLE_TODAY",
                "INVOICES", "TEACHER_TODAY", "TEACHER_ROLL", "EVENTS", "NONE"]
STUDENT_INTENTS = {"ATTENDANCE_TODAY", "ATTENDANCE_SUMMARY", "GRADES", "EXAMS", "TIMETABLE_TODAY"}
# Same rules as SubjectResolver: a student never gets INVOICES; a teacher gets TEACHER_*;
# every role gets EVENTS.
ROLE_INTENTS = {
    "PARENT": STUDENT_INTENTS | {"INVOICES", "EVENTS"},
    "STUDENT": STUDENT_INTENTS | {"EVENTS"},
    "TEACHER": {"TEACHER_TODAY", "TEACHER_ROLL", "EVENTS"},
}
THRESHOLDS = [0.5, 0.6, 0.7, 0.8, 0.9]
# Production timeouts (D2): primary 3 s, fallback 5 s.
TIMEOUTS = {"decision": 3.0, "openai": 5.0}
THINK = re.compile(r"<think>.*?</think>", re.DOTALL)


class EvalError(Exception):
    """A failed call; the message is only the failure kind."""


def load_instructions():
    with open(os.path.join(LLM_DIR, "instructions.txt"), encoding="utf-8") as f:
        return "\n".join(f.read().splitlines()).strip()


def load_hints():
    hints = {}
    with open(os.path.join(LLM_DIR, "intent-hints.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            hints[key.strip()] = value.strip()
    missing = [k for k in INTENT_ORDER if k not in hints]
    if missing:
        sys.exit("intent-hints.properties lacks: " + ", ".join(missing))
    return {k: hints[k] for k in INTENT_ORDER}


def load_rows(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def split_list(value):
    return [v.strip() for v in value.split(",") if v.strip()]


def intent_options(row, hints):
    allowed = set()
    for role in split_list(row["roles"]):
        allowed |= ROLE_INTENTS[role]
    return {k: v for k, v in hints.items() if k == "NONE" or k in allowed}


def target_labels(row):
    labels = split_list(row["labels"])
    # The target question is asked only with at least two labels (FreeTextResponder).
    return labels + ["NONE"] if len(labels) >= 2 else []


def decision_body(model, row, instructions, hints):
    questions = {"intent": {"type": "choice", "instructions": instructions,
                            "criteria": intent_options(row, hints)}}
    labels = target_labels(row)
    if labels:
        questions["target"] = {"type": "choice", "instructions": instructions,
                               "criteria": {label: None for label in labels}}
    return {"model": model, "state": row["text"], "questions": questions}


def system_prompt(row, instructions, hints):
    out = [instructions, "\n\nIntents:\n"]
    for name, hint in intent_options(row, hints).items():
        out.append("- %s: %s\n" % (name, hint))
    labels = target_labels(row)
    if labels:
        out.append("\nTargets:\n")
        for label in labels:
            out.append("- %s\n" % label)
        out.append('\nReply with exactly one JSON object: {"intent":"NAME","target":"label-or-NONE"}')
    else:
        out.append('\nReply with exactly one JSON object: {"intent":"NAME"}')
    return "".join(out)


def openai_body(model, row, instructions, hints):
    return {"model": model, "temperature": 0, "messages": [
        {"role": "system", "content": system_prompt(row, instructions, hints)},
        {"role": "user", "content": row["text"] + " /no_think"}]}


def post(url, key, body, timeout):
    request = urllib.request.Request(
        url, data=json.dumps(body, ensure_ascii=False).encode("utf-8"), method="POST",
        headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
    except urllib.error.HTTPError as e:
        raise EvalError("http %d" % e.code)
    except (TimeoutError, OSError) as e:
        reason = getattr(e, "reason", e)
        raise EvalError("timeout" if isinstance(reason, TimeoutError) or "timed out" in str(reason)
                        else "connection")
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except ValueError:
        raise EvalError("unparsable")
    if not isinstance(parsed, dict):
        raise EvalError("unparsable")
    return parsed


def usage_of(parsed):
    usage = parsed.get("usage")
    if not isinstance(usage, dict):
        return 0, 0
    tokens_in = usage.get("prompt_tokens", usage.get("input_tokens", 0))
    tokens_out = usage.get("completion_tokens", usage.get("output_tokens", 0))
    return (tokens_in if isinstance(tokens_in, int) else 0,
            tokens_out if isinstance(tokens_out, int) else 0)


def classify_decision(base, path, key, model, row, instructions, hints):
    parsed = post(base.rstrip("/") + path, key, decision_body(model, row, instructions, hints),
                  TIMEOUTS["decision"])
    answers = parsed.get("answers") or {}
    intent = answers.get("intent") or {}
    if not isinstance(intent.get("choice"), str):
        raise EvalError("unparsable")
    target = target_conf = None
    if target_labels(row):
        t = answers.get("target") or {}
        if not isinstance(t.get("choice"), str):
            raise EvalError("unparsable")
        target, target_conf = t["choice"], float(t.get("confidence") or 0)
    return intent["choice"], float(intent.get("confidence") or 0), target, target_conf, usage_of(parsed)


def classify_openai(base, key, model, row, instructions, hints):
    parsed = post(base.rstrip("/") + "/chat/completions", key,
                  openai_body(model, row, instructions, hints), TIMEOUTS["openai"])
    try:
        content = parsed["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        raise EvalError("unparsable")
    if not isinstance(content, str):
        raise EvalError("unparsable")
    text = THINK.sub("", content)
    start = text.find("{")
    end = text.find("}", start) if start >= 0 else -1
    if end < 0:
        raise EvalError("unparsable")
    try:
        answer = json.loads(text[start:end + 1])
    except ValueError:
        raise EvalError("unparsable")
    if not isinstance(answer, dict) or not isinstance(answer.get("intent"), str) or not answer["intent"].strip():
        raise EvalError("unparsable")
    target = None
    if target_labels(row):
        if not isinstance(answer.get("target"), str):
            raise EvalError("unparsable")
        target = answer["target"]
    return answer["intent"], 1.0, target, 1.0 if target is not None else None, usage_of(parsed)


def target_scored(row):
    return len(split_list(row["labels"])) >= 2 and row["expected_intent"] != "NONE"


def correct(row, intent, target, with_target):
    if intent != row["expected_intent"]:
        return False
    if with_target and target_scored(row):
        return target == row["expected_target"]
    return True


def effective(row, intent, conf, target, target_conf, threshold):
    """What the bot would act on at this threshold (ClassificationValidator): an intent below it
    becomes NONE (menu); a target below it, or NONE, leaves the intent and asks with buttons; a
    label that was not offered turns the whole answer into NONE."""
    if conf < threshold:
        return "NONE", None
    if intent != "NONE" and target is not None and target != "NONE":
        if target not in split_list(row["labels"]):
            return "NONE", None
        if target_conf is not None and target_conf < threshold:
            return intent, "NONE"
    return intent, target


def percentile95(values):
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, math.ceil(0.95 * len(ordered)) - 1)]


def evaluate(model, adapter, rows, args, key, instructions, hints):
    results, latencies, errors = [], [], 0
    tokens_in = tokens_out = 0
    for index, row in enumerate(rows, 1):
        start = time.monotonic()
        try:
            if adapter == "decision":
                result = classify_decision(args.base_url, args.decision_path, key, model, row,
                                           instructions, hints)
            else:
                result = classify_openai(args.base_url, key, model, row, instructions, hints)
        except EvalError as e:
            errors += 1
            results.append(None)
            print("  row %d: error (%s)" % (index, e), file=sys.stderr)
        else:
            results.append(result)
            tokens_in += result[4][0]
            tokens_out += result[4][1]
        latencies.append((time.monotonic() - start) * 1000)
    total = len(rows)

    def acc(with_target, threshold=None):
        hit = 0
        for row, res in zip(rows, results):
            if res is None:
                continue
            intent, conf, target, target_conf, _ = res
            if threshold is not None:
                intent, target = effective(row, intent, conf, target, target_conf, threshold)
            hit += correct(row, intent, target, with_target)
        return 100.0 * hit / total

    print("== %s (%s) ==" % (model, adapter))
    print("rows: %d   errors: %d" % (total, errors))
    print("accuracy intent: %.1f%%   intent+target: %.1f%%" % (acc(False), acc(True)))
    if adapter == "decision":
        for t in THRESHOLDS:
            print("  at confidence >= %.1f: intent %.1f%%   intent+target %.1f%%"
                  % (t, acc(False, t), acc(True, t)))
    else:
        print("  confidence thresholds: not applicable (no confidence from this adapter)")
    print("latency ms: mean %.0f   p95 %.0f" % (sum(latencies) / total if total else 0, percentile95(latencies)))
    print("tokens: input %d   output %d" % (tokens_in, tokens_out))
    print()


def main():
    parser = argparse.ArgumentParser(description="Evaluate the Bale bot router on the evaluation set.")
    parser.add_argument("--model", action="append", required=True, help="model name; repeatable")
    parser.add_argument("--adapter", action="append", required=True, choices=["decision", "openai"],
                        help="adapter for the model at the same position; repeatable")
    parser.add_argument("--decision-path", default="/systemone",
                        help="decision API path relative to BOT_LLM_BASE_URL (default /systemone)")
    parser.add_argument("--set", default=DEFAULT_SET, help="evaluation TSV")
    parser.add_argument("--dry-run", action="store_true",
                        help="print a sample request body per adapter (all hints, two labels) and exit; no network")
    args = parser.parse_args()
    if len(args.model) != len(args.adapter):
        parser.error("give one --adapter for every --model")
    args.base_url = os.environ.get("BOT_LLM_BASE_URL", "")
    key = os.environ.get("BOT_LLM_API_KEY", "")

    instructions, hints, rows = load_instructions(), load_hints(), load_rows(args.set)
    if not rows:
        sys.exit("evaluation set is empty")

    if args.dry_run:
        # A sample that shows every hint and the target question: all roles, two labels.
        first = dict(rows[0], roles="PARENT,TEACHER", labels="[فرزند_۱],[فرزند_۲]")
        shown = set()
        for model, adapter in zip(args.model, args.adapter):
            if adapter in shown:
                continue
            shown.add(adapter)
            body = (decision_body(model, first, instructions, hints) if adapter == "decision"
                    else openai_body(model, first, instructions, hints))
            print("# %s adapter, POST %s" % (adapter, args.decision_path if adapter == "decision"
                                              else "/chat/completions"))
            print(json.dumps(body, ensure_ascii=False, indent=2))
        return 0

    if not args.base_url or not key:
        sys.exit("set BOT_LLM_BASE_URL and BOT_LLM_API_KEY in the environment")
    for model, adapter in zip(args.model, args.adapter):
        evaluate(model, adapter, rows, args, key, instructions, hints)
    return 0


if __name__ == "__main__":
    sys.exit(main())
