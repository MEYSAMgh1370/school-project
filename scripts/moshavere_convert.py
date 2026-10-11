#!/usr/bin/env python3
"""Offline converter for Phase 1 of the Moshavere import.

Reads the canonical tree (tree v2, HTML) and the official grade 10-12 course
CSV, and writes data/moshavere/bundle_phase1.json. It imports nothing.

Run from the repo root:  python3 -I scripts/moshavere_convert.py
Standard library only.
"""
import argparse
import csv
import json
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TREE = ROOT / "data" / "moshavere" / "tree_v2_utf8.html"
DEFAULT_CSV = ROOT / "data" / "moshavere" / "official_grade10_12.csv"
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


def build(tree_path, csv_path):
    topics = parse_tree(tree_path)
    courses = parse_courses(csv_path)
    report = match_courses(courses, topics)
    bundle = {"format": FORMAT, "source": SOURCE, "topics": topics, "courses": courses}
    return bundle, report


def dump(bundle):
    return json.dumps(bundle, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tree", type=Path, default=DEFAULT_TREE)
    ap.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args(argv)
    try:
        bundle, report = build(args.tree, args.csv)
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
    for line in report:
        print(line)
    print("wrote %s" % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
