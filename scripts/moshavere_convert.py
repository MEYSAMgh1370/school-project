#!/usr/bin/env python3
"""Offline converter for Phase 1 of the Moshavere import.

Reads the canonical tree (tree v2, HTML), the official grade 10-12 course CSV
and the corrected question sheet, and writes data/moshavere/bundle_phase1.json.
It imports nothing.

Run from the repo root:  python3 -I scripts/moshavere_convert.py
Standard library only.
"""
import argparse
import csv
import json
import re
import sys
from html import unescape as html_unescape
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import moshavere_math_questions as math_questions  # noqa: E402  (sibling module; works under python3 -I)
DEFAULT_TREE = ROOT / "data" / "moshavere" / "tree_v2_utf8.html"
DEFAULT_CSV = ROOT / "data" / "moshavere" / "official_grade10_12.csv"
DEFAULT_QUESTIONS = ROOT / "data" / "moshavere" / "questions_f12e8f54_chemistry3.html"
DEFAULT_OUT = ROOT / "data" / "moshavere" / "bundle_phase1.json"

FORMAT = 1
SOURCE = "moshaver-tree-v2"

# Persian grade words and digit forms accepted in the CSV grade column.
GRADES = {
    "دهم": 10,  # دهم
    "یازدهم": 11,  # یازدهم
    "دوازدهم": 12,  # دوازدهم
    "10": 10,
    "11": 11,
    "12": 12,
}
TRACKS = {
    "ریاضی",  # ریاضی
    "تجربی",  # تجربی
    "انسانی",  # انسانی
}
COMMON = "common"

_DIGITS = str.maketrans(
    "۰۱۲۳۴۵۶۷۸۹"
    "٠١٢٣٤٥٦٧٨٩",
    "0123456789" * 2,
)
_CODE_RE = re.compile(r"^(.*?)\s*\(([^()]*)\)\s*$", re.S)
_PAYE_RE = re.compile(
    r"\s*پایه\s+(?:دهم|یازدهم"
    r"|دوازدهم|\d+)"
)


class ConversionError(Exception):
    pass


def collapse(s):
    return re.sub(r"\s+", " ", s).strip()


def normalise(s):
    """Comparison-only normalisation."""
    s = s.replace("ي", "ی").replace("ك", "ک")
    s = s.replace("‌", "").replace("،", ",")
    s = s.translate(_DIGITS)
    s = _PAYE_RE.sub("", s)
    return collapse(s)


class _TreeParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.roots = []

    def handle_starttag(self, tag, attrs):
        if tag == "li":
            self.stack.append({"text": [], "children": [], "ul": False})
        elif tag == "ul" and self.stack:
            self.stack[-1]["ul"] = True

    def handle_data(self, data):
        if self.stack and not self.stack[-1]["ul"]:
            self.stack[-1]["text"].append(data)

    def handle_endtag(self, tag):
        if tag == "li" and self.stack:
            node = self.stack.pop()
            (self.stack[-1]["children"] if self.stack else self.roots).append(node)


def parse_tree(path):
    """Return a flat depth-first topic list; raises ConversionError on bad data."""
    parser = _TreeParser()
    parser.feed(Path(path).read_text(encoding="utf-8"))
    parser.close()
    topics = []
    seen = {}

    def walk(node, parent_code, order):
        text = collapse("".join(node["text"]))
        m = _CODE_RE.match(text)
        if not m or not m.group(2).strip():
            raise ConversionError("node has no code: %r" % text)
        name, code = collapse(m.group(1)), m.group(2).strip()
        if code in seen:
            raise ConversionError("duplicate code %s (%r and %r)" % (code, seen[code], name))
        seen[code] = name
        topics.append({"sourceCode": code, "parentSourceCode": parent_code,
                       "name": name, "sortOrder": order})
        for i, child in enumerate(node["children"]):
            walk(child, code, i)

    def check_siblings(nodes_by_parent):
        for parent, names in nodes_by_parent.items():
            seen_names = set()
            for n in names:
                if n in seen_names:
                    raise ConversionError(
                        "sibling nodes share the name %r under parent %s" % (n, parent))
                seen_names.add(n)

    for i, root in enumerate(parser.roots):
        walk(root, None, i)
    by_parent = {}
    for t in topics:
        by_parent.setdefault(t["parentSourceCode"], []).append(t["name"])
    check_siblings(by_parent)
    return topics


SKIPPED_OUT_OF_SCOPE = 0

QUESTION_STEM = "کدام یک از گزینه‌های زیر صحیح است."
QUESTION_SOURCE = "f12e8f54"
# Reviewed correction (D14): the Zn–SHE statement is true, so the distractor is
# reworded to the anode version. Each match must be found exactly once.
ZN_SHE_OLD = "در سلول گالوانی $\\displaystyle Zn-SHE $ هیدروژن نقش کاتد را ایفا می‌کند."
ZN_SHE_NEW = "در سلول گالوانی $\\displaystyle Zn-SHE $ هیدروژن نقش آند را ایفا می‌کند."
ZN_SHE_QUESTIONS = {1, 6, 9}
# Topic of each question (reviewed by hand, D16). The sheet's own «مبحث» labels are not
# used: for question 3 the label names the fuel-cell topic, not the electrochemical cell.
QUESTION_TOPICS = {
    1: "24144",   # صابون؛ مزایا و معایب آن
    2: "24183",   # انرژی شبکه بلور و عوامل موثر بر آن
    3: "23012",   # مفاهیم کلی و مقایسه‌ی آن با سلول گالوانی
    4: "24208",   # ترکیبی (تعادل)
    5: "24183",   # انرژی شبکه بلور و عوامل موثر بر آن
    6: "24176",   # گرافیت و الماس
    7: "24193",   # کاتالیزگر و تاثیر آن بر نمودار انرژی - مسیر واکنش ها
    8: "24176",   # گرافیت و الماس
    9: "24158",   # روابط pH و [H₃O⁺] و مسائل مربوط
    10: "24193",  # کاتالیزگر و تاثیر آن بر نمودار انرژی - مسیر واکنش ها
}


def parse_courses(path):
    courses = []
    seen = set()
    global SKIPPED_OUT_OF_SCOPE
    SKIPPED_OUT_OF_SCOPE = 0
    with open(path, encoding="utf-8-sig", newline="") as f:
        for lineno, row in enumerate(csv.DictReader(f), start=2):
            grade_raw = collapse(row.get("grade") or "").translate(_DIGITS)
            track_raw = collapse(row.get("track") or "")
            grade = GRADES.get(grade_raw)
            if grade is None:
                raise ConversionError("line %d: grade outside 10-12: %r" % (lineno, grade_raw))
            if track_raw == COMMON:
                track = None
            elif track_raw in TRACKS:
                track = track_raw
            elif track_raw == "معارف":
                # The Islamic-studies group is outside the three tracks in scope (O14); skipped, not refused.
                SKIPPED_OUT_OF_SCOPE += 1
                continue
            else:
                raise ConversionError("line %d: track not allowed: %r" % (lineno, track_raw))
            name = collapse(row.get("title") or "")
            if not name:
                raise ConversionError("line %d: empty course title" % lineno)
            key = (grade, name, track)
            if key in seen:
                continue
            seen.add(key)
            courses.append({"gradeLevel": grade, "name": name, "track": track,
                            "topicSourceCode": None})
    return courses


def _html_text(path):
    raw = Path(path).read_text(encoding="utf-8")
    raw = re.sub(r"<[^>]+>", " ", raw)
    return collapse(html_unescape(raw))


def _split_block(block):
    """Return (choices, notes, answer_no) for one flattened question block."""
    body = block[len(QUESTION_STEM):]
    answer_at = body.rfind("پس گزینه‌ی")
    if answer_at == -1:
        raise ConversionError("question block has no answer line: %r" % body[:60])
    answer_line = body[answer_at:]
    body = body[:answer_at]
    starts = []
    cursor = 0
    for k in range(1, 5):
        marker = ("%d- " % k) if k == 1 else (" %d- " % k)
        found = body.find(marker, cursor)
        if found == -1:
            raise ConversionError("choice %d not found" % k)
        starts.append(found + len(marker))
        cursor = found + len(marker)
    choices = []
    for k in range(1, 5):
        end = starts[k] - len(" %d- " % (k + 1)) if k < 4 else body.index("گزینه‌ی 1 :")
        choices.append(collapse(body[starts[k - 1]:end]))
    notes = []
    for k in range(1, 5):
        head = "گزینه‌ی %d : " % k
        rest = body[body.index(head) + len(head):]
        end = rest.index("گزینه‌ی %d : " % (k + 1)) if k < 4 else len(rest)
        text = collapse(rest[:end])
        if text.startswith("نادرست"):
            verdict = False
        elif text.startswith("درست"):
            verdict = True
        else:
            raise ConversionError("choice %d has no verdict" % k)
        notes.append({"verdict": verdict})
    answer = re.match(r"پس گزینه‌ی (\d) صحیح است", answer_line)
    if not answer:
        raise ConversionError("answer line not recognised: %r" % answer_line[:60])
    return choices, notes, int(answer.group(1))


def parse_questions(path, topics):
    """Return the bundle's question list. Refuses on any ambiguity."""
    blocks = _html_text(path).split(QUESTION_STEM)[1:]
    if not blocks:
        raise ConversionError("no questions found in %s" % Path(path).name)
    known = {t["sourceCode"] for t in topics}
    questions = []
    for number, block in enumerate(blocks, start=1):
        choices, notes, answer_no = _split_block(QUESTION_STEM + block)
        if number in ZN_SHE_QUESTIONS:
            hits = [i for i, c in enumerate(choices) if c == collapse(ZN_SHE_OLD)]
            if len(hits) != 1:
                raise ConversionError("question %d: Zn-SHE statement found %d times" % (number, len(hits)))
            choices[hits[0]] = collapse(ZN_SHE_NEW)
            notes[hits[0]] = {"verdict": False}
        true_idx = [i for i, n in enumerate(notes) if n["verdict"]]
        if len(true_idx) != 1 or true_idx[0] + 1 != answer_no:
            raise ConversionError("question %d: verdicts do not give the answer line's choice" % number)
        correct = true_idx[0]
        topic = QUESTION_TOPICS.get(number)
        if topic not in known:
            raise ConversionError("question %d: topic %r is not in the tree" % (number, topic))
        for c in choices:
            if not c or re.search(r"\bnan\b|C40", c):
                raise ConversionError("question %d: empty choice or stray token: %r" % (number, c))
        questions.append({
            "sourceCode": "%s:Q%02d" % (QUESTION_SOURCE, number),
            "topicSourceCode": topic,
            "text": QUESTION_STEM,
            "choices": choices,
            "correctChoice": correct + 1,
            "answer": None,
        })
    return questions


def match_courses(courses, topics):
    """Fill topicSourceCode on exact normalised-name match. Return report lines."""
    index = {}
    for t in topics:
        index.setdefault(normalise(t["name"]), []).append(t["sourceCode"])
    report = []
    for c in courses:
        cands = index.get(normalise(c["name"]), [])
        if len(cands) == 1:
            c["topicSourceCode"] = cands[0]
        elif not cands:
            report.append("NO MATCH   grade %d track %s: %s" % (c["gradeLevel"], c["track"], c["name"]))
        else:
            report.append("AMBIGUOUS  grade %d track %s: %s -> %s" % (
                c["gradeLevel"], c["track"], c["name"], ", ".join(cands)))
    return report


def qualify_track_names(courses):
    """Add the track to each track-specific course name, as in the tree (D6, O14).

    The platform key is (grade, name). Without the track, a name shared by two
    tracks (for example «فیزیک (3)» in ریاضی and تجربی) would overwrite one row.
    Runs after matching, so the topic match still uses the sheet's own name.
    """
    for c in courses:
        if c["track"] is not None:
            c["name"] = "%s (%s)" % (c["name"], c["track"])
    seen = set()
    for c in courses:
        key = (c["gradeLevel"], c["name"])
        if key in seen:
            raise ConversionError("duplicate course key after qualifying: grade %d %r" % key)
        seen.add(key)


def parse_math_questions(topics):
    """Maths questions from the gosaste-4 sheet, reviewed by hand (see moshavere_math_questions.py)."""
    known = {t["sourceCode"] for t in topics}
    out = []
    for number, text, choices, correct, topic, _note in math_questions.QUESTIONS:
        if len(choices) != 4 or not 1 <= correct <= 4:
            raise ConversionError("math question %d: needs four choices and a key 1-4" % number)
        if topic not in known:
            raise ConversionError("math question %d: topic %s is not in the tree" % (number, topic))
        out.append({
            "sourceCode": "gosaste-4:Q%02d" % number,
            "topicSourceCode": topic,
            "text": text,
            "choices": list(choices),
            "correctChoice": correct,
            "answer": None,
        })
    return out


def build(tree_path, csv_path, questions_path=None, math=False):
    topics = parse_tree(tree_path)
    courses = parse_courses(csv_path)
    report = match_courses(courses, topics)
    qualify_track_names(courses)
    questions = parse_questions(questions_path, topics) if questions_path else []
    if math:
        questions += parse_math_questions(topics)
    bundle = {"format": FORMAT, "source": SOURCE, "topics": topics, "courses": courses,
              "questions": questions}
    return bundle, report
    return bundle, report


def dump(bundle):
    return json.dumps(bundle, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tree", type=Path, default=DEFAULT_TREE)
    ap.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    ap.add_argument("--questions", type=Path, default=DEFAULT_QUESTIONS)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args(argv)
    try:
        bundle, report = build(args.tree, args.csv, args.questions, math=True)
    except ConversionError as e:
        print("REFUSED: %s" % e)
        return 1
    args.out.write_text(dump(bundle), encoding="utf-8", newline="\n")
    roots = sum(1 for t in bundle["topics"] if t["parentSourceCode"] is None)
    unmatched = sum(1 for c in bundle["courses"] if c["topicSourceCode"] is None)
    print("topics: %d" % len(bundle["topics"]))
    print("roots: %d" % roots)
    print("courses: %d" % len(bundle["courses"]))
    print("skipped out-of-scope (معارف) rows: %d" % SKIPPED_OUT_OF_SCOPE)
    print("courses with null topic match: %d" % unmatched)
    print("questions: %d" % len(bundle["questions"]))
    for line in report:
        print(line)
    print("wrote %s" % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
