#!/usr/bin/env python3
"""
Populate a Roshdeandishe deployment with realistic demo data, for a
presentation or a walkthrough.

Everything is created through the public REST API, exactly as a real admin
would: register -> log in -> create a school -> fill it. Nothing here bypasses
the domain rules, so the result can never be in a state the app itself would
refuse to produce.

Deliberately NOT a seed runner. The `prod` profile skips demo seeding on
purpose (CLAUDE.md §7 item 5): a demo admin baked into the image is a public
SCHOOL_ADMIN with a known password on the open internet. This script instead
creates an ordinary account whose password *you* supply at run time and can
change or delete afterwards.

Usage:
    python3 scripts/seed-demo-data.py \\
        --url https://your-app.liara.run \\
        --email demo@example.com \\
        --password 'a-strong-password'

Re-running is safe: an existing account is reused, an existing school is
reused, students are idempotent by email, and classes are matched by name.

The three paid modules (CURRICULUM / STUDY_PLANNER / EXAM) cannot be granted
over HTTP — that is a deliberate architectural restriction (CLAUDE.md §10), so
this script prints the SQL for them and continues without failing. Run that
SQL, then re-run this script to seed the curriculum and exam content too.

Standard library only. No pip install.
"""

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from http.cookiejar import CookieJar

# ---------------------------------------------------------------------------
# Demo content. Persian throughout, since that is what the UI renders.
# ---------------------------------------------------------------------------

STUDENT_NAMES = [
    "علی رضایی", "زهرا محمدی", "امیرحسین کریمی", "فاطمه حسینی", "محمد موسوی",
    "نرگس اکبری", "سینا جعفری", "مریم صادقی", "رضا قاسمی", "سارا نوری",
    "پارسا احمدی", "یاسمن شریفی", "کیان مرادی", "هستی رحیمی", "آرش کاظمی",
    "ملیکا زارعی", "دانیال بابایی", "نیایش سلطانی", "بردیا فتحی", "ترنم عزیزی",
    "طاها یوسفی", "آیناز خانی", "سبحان نجفی", "رها شکوهی", "متین باقری",
    "پریسا امینی", "احسان طاهری", "کوثر مهدوی", "آرمین سعیدی", "دیانا قربانی",
]

TEACHER_NAMES = ["خانم سمیه توکلی", "آقای مهدی رستمی", "خانم لیلا اسدی"]

# (topic, [subtopics]) — a small but real-looking primary-school taxonomy.
TOPICS = [
    ("ریاضی", ["اعداد و الگوها", "کسرها", "هندسه", "اندازه‌گیری"]),
    ("علوم تجربی", ["مواد و تغییرات", "زیست و محیط", "انرژی"]),
    ("فارسی", ["خوانداری", "نگارش", "دستور زبان"]),
]

# Questions are keyed by subtopic name; 4 choices, 1-based correct index.
QUESTIONS = {
    "کسرها": [
        ("حاصل ۱/۲ + ۱/۴ چند است؟", ["۱/۶", "۳/۴", "۲/۶", "۱/۸"], 2,
         "مخرج مشترک ۴ است: ۲/۴ + ۱/۴ = ۳/۴."),
        ("کدام کسر بزرگ‌تر است؟", ["۲/۵", "۳/۵", "۱/۵", "۴/۱۰"], 2,
         "با مخرج برابر، صورت بزرگ‌تر یعنی کسر بزرگ‌تر."),
        ("کسر ۶/۸ ساده‌شده کدام است؟", ["۳/۴", "۲/۳", "۴/۶", "۱/۲"], 1,
         "صورت و مخرج را بر ۲ تقسیم می‌کنیم."),
    ],
    "هندسه": [
        ("مجموع زوایای داخلی مثلث چند درجه است؟", ["۹۰", "۱۸۰", "۲۷۰", "۳۶۰"], 2,
         "در هر مثلث مجموع زوایای داخلی ۱۸۰ درجه است."),
        ("مربعی به ضلع ۵ سانتی‌متر، محیطش چند است؟", ["۱۰", "۱۵", "۲۰", "۲۵"], 3,
         "محیط مربع برابر ۴ برابر ضلع است: ۴ × ۵ = ۲۰."),
    ],
    "اعداد و الگوها": [
        ("عدد بعدی الگو چیست؟ ۲، ۴، ۸، ۱۶، ...", ["۱۸", "۲۴", "۳۲", "۲۰"], 3,
         "هر عدد دو برابر عدد قبلی است."),
        ("کدام عدد بر ۳ بخش‌پذیر است؟", ["۱۷", "۲۲", "۲۷", "۳۱"], 3,
         "مجموع ارقام ۲۷ برابر ۹ است که بر ۳ بخش‌پذیر است."),
    ],
    "مواد و تغییرات": [
        ("کدام یک تغییر شیمیایی است؟", ["ذوب یخ", "سوختن چوب", "خرد کردن شیشه", "تبخیر آب"], 2,
         "در سوختن، ماده‌ی جدید تولید می‌شود."),
    ],
    "انرژی": [
        ("منبع اصلی انرژی زمین کدام است؟", ["ماه", "خورشید", "باد", "زغال‌سنگ"], 2,
         "تقریباً همه‌ی انرژی‌های زمین ریشه در خورشید دارند."),
    ],
    "خوانداری": [
        ("مترادف «شجاع» کدام است؟", ["ترسو", "دلیر", "آرام", "تنبل"], 2,
         "«دلیر» هم‌معنی «شجاع» است."),
    ],
}

DESCRIPTIVE_LEVELS = ["EXCELLENT", "GOOD", "ACCEPTABLE", "NEEDS_EFFORT"]
# Weighted so a demo gradebook looks like a real class, not a uniform sample.
DESCRIPTIVE_WEIGHTS = [4, 5, 3, 1]

GRANT_PAID_MODULES_SQL = """\
-- Paid modules have no REST endpoint by design (CLAUDE.md §10) — granting
-- them is a platform-privileged action and the permission infrastructure for
-- it does not exist yet. Run this in PGAdmin, then re-run this script.
INSERT INTO iam.school_entitlements (school_id, module_id, active, starts_at)
SELECT {school_id}, id, true, now()
FROM iam.modules
WHERE code IN ('CURRICULUM', 'STUDY_PLANNER', 'EXAM')
ON CONFLICT (school_id, module_id) DO UPDATE SET active = true;\
"""


class ApiError(Exception):
    def __init__(self, status, body, path):
        self.status = status
        self.body = body
        self.path = path
        super().__init__(f"HTTP {status} on {path}: {body}")

    @property
    def problem_type(self):
        """The ProblemDetail `type` URI suffix, e.g. 'module-not-entitled'."""
        try:
            return json.loads(self.body).get("type", "").rsplit("/", 1)[-1]
        except Exception:
            return ""


class Client:
    """Session-cookie + CSRF client, matching what the JTE pages themselves do."""

    def __init__(self, base_url):
        self.base = base_url.rstrip("/")
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar)
        )
        self.school_id = None

    def _csrf(self):
        for cookie in self.jar:
            if cookie.name == "XSRF-TOKEN":
                return cookie.value
        return None

    def request(self, method, path, body=None, school_scoped=True):
        url = self.base + path
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        token = self._csrf()
        if token and method != "GET":
            req.add_header("X-XSRF-TOKEN", token)
        # Every school-scoped endpoint checks this header against the path
        # variable (TenantContext.requireMatches) — a mismatch is a 403.
        if school_scoped and self.school_id is not None:
            req.add_header("X-School-ID", str(self.school_id))
        try:
            with self.opener.open(req, timeout=60) as resp:
                raw = resp.read().decode()
                # The landing page is fetched only to pick up the XSRF-TOKEN
                # cookie, and it returns HTML — so parse only real JSON.
                if "json" not in resp.headers.get("Content-Type", ""):
                    return None
                return json.loads(raw) if raw.strip() else None
        except urllib.error.HTTPError as e:
            raise ApiError(e.code, e.read().decode(errors="replace"), path) from None
        except urllib.error.URLError as e:
            raise SystemExit(f"\n  Cannot reach {url}: {e.reason}\n"
                             f"  Is the app deployed and the URL right?")

    def get(self, path, **kw):
        return self.request("GET", path, **kw)

    def post(self, path, body=None, **kw):
        return self.request("POST", path, body, **kw)

    def put(self, path, body=None, **kw):
        return self.request("PUT", path, body, **kw)


def step(msg):
    print(f"  {msg}", flush=True)


def main():
    p = argparse.ArgumentParser(
        description="Seed a Roshdeandishe deployment with demo data.")
    p.add_argument("--url", required=True,
                   help="Base URL, e.g. https://your-app.liara.run")
    p.add_argument("--email", required=True, help="Demo account email")
    p.add_argument("--password", required=True,
                   help="Demo account password (min 8 chars)")
    p.add_argument("--full-name", default="مدیر نمایشی")
    p.add_argument("--school-name", default="دبستان رشد اندیشه")
    p.add_argument("--slug", default="roshd-demo",
                   help="Branded entry URL segment: /s/<slug>")
    p.add_argument("--students", type=int, default=24,
                   help="How many students to enroll (default 24)")
    p.add_argument("--academic-year", default="1404-1405")
    args = p.parse_args()

    if len(args.password) < 8:
        raise SystemExit("Password must be at least 8 characters "
                         "(the app enforces this on registration).")
    if args.students > len(STUDENT_NAMES):
        raise SystemExit(f"--students cannot exceed {len(STUDENT_NAMES)} "
                         f"(that is how many demo names are defined).")

    random.seed(1404)  # stable output across runs
    c = Client(args.url)

    print(f"\nSeeding {c.base}\n")

    # -- account ------------------------------------------------------------
    print("Account")
    c.get("/", school_scoped=False)  # obtain the XSRF-TOKEN cookie
    try:
        c.post("/api/auth/register", {
            "email": args.email,
            "fullName": args.full_name,
            "password": args.password,
        }, school_scoped=False)
        step(f"registered {args.email}")
    except ApiError as e:
        if e.status == 409:
            step(f"{args.email} already exists — reusing it")
        else:
            raise

    try:
        c.post("/api/auth/login", {
            "email": args.email,
            "password": args.password,
        }, school_scoped=False)
    except ApiError as e:
        if e.status == 401:
            raise SystemExit(
                f"\n  Login failed for {args.email}.\n"
                f"  The account exists but this password is wrong — pass the "
                f"original one, or use a different --email.")
        raise
    step("logged in")

    # -- school -------------------------------------------------------------
    print("\nSchool")
    memberships = c.get("/api/me/memberships", school_scoped=False) or []
    admin_of = [m for m in memberships
                if m["roleCode"] == "SCHOOL_ADMIN" and m["active"]]
    if admin_of:
        c.school_id = admin_of[0]["schoolId"]
        step(f"reusing existing school #{c.school_id}")
    else:
        school = c.post("/api/admin/schools", {
            "name": args.school_name,
            "timezone": "Asia/Tehran",
            "locale": "fa-IR",
            "level": "ELEMENTARY",
            "slug": args.slug,
        }, school_scoped=False)
        c.school_id = school["id"]
        step(f"created «{school['name']}» (#{c.school_id}), entry page /s/{school['slug']}")
        step("free tier granted automatically: ACADEMIC + FINANCE")

    sid = c.school_id
    base = f"/api/admin/schools/{sid}"

    # -- classes ------------------------------------------------------------
    # ELEMENTARY, so gradeLevel must be 1..6 and the grading scheme defaults to
    # DESCRIPTIVE (derived from gradeLevel — CLAUDE.md §4, Class).
    print("\nClasses")
    existing = {k["name"]: k for k in (c.get(f"{base}/classes") or [])}
    classes = []
    for grade, section in [(4, "الف"), (4, "ب"), (5, "الف"), (6, "الف")]:
        name = f"پایه {grade} - {section}"
        if name in existing:
            classes.append(existing[name])
            step(f"{name} already exists")
            continue
        created = c.post(f"{base}/classes", {
            "name": name,
            "section": section,
            "gradeLevel": grade,
            "teacherId": None,
            "gradingScheme": None,  # let the server derive it from gradeLevel
        })
        classes.append(created)
        step(f"{name} (scheme: {created.get('gradingScheme', 'DESCRIPTIVE')})")

    # -- tuition, before enrolling ------------------------------------------
    # Order matters: enrollment publishes StudentEnrolledEvent, and finance
    # only issues an invoice if a fee already exists. Setting it first means
    # invoices appear on their own.
    print("\nFinance")
    c.put(f"{base}/tuition-fee", {"amount": 45000000})
    step("tuition fee set to ۴۵٬۰۰۰٬۰۰۰ ریال")

    # -- students -----------------------------------------------------------
    # Only enrol students that do not exist yet. enrollStudent is idempotent
    # for the *student* (same email updates their class rather than
    # duplicating), but it republishes StudentEnrolledEvent every time, and
    # finance answers that by issuing another invoice — so blindly re-running
    # would double the invoices.
    print("\nStudents")
    already = c.get(f"{base}/students") or []
    students = [{**s, "name": None} for s in already]
    if len(already) >= args.students:
        step(f"{len(already)} students already enrolled — reusing them")
    else:
        for i in range(len(already), args.students):
            klass = classes[i % len(classes)]
            s = c.post(f"{base}/students", {
                "email": f"student{i + 1}@{args.slug}.demo",
                "fullName": STUDENT_NAMES[i],
                "classId": klass["id"],
            })
            students.append({**s, "name": STUDENT_NAMES[i]})
        step(f"enrolled {args.students - len(already)} students "
             f"across {len(classes)} classes")

    # The invoice listener is async (@ApplicationModuleListener, after commit),
    # so give it a moment, then backfill as a safety net. Backfill is
    # idempotent and only touches students without an invoice.
    time.sleep(3)
    try:
        c.post(f"{base}/invoices/backfill")
    except ApiError as e:
        if e.problem_type != "tuition-fee-not-set":
            raise
    invoices = c.get(f"{base}/invoices") or []
    step(f"{len(invoices)} tuition invoices issued")

    # -- payments: produce a realistic PAID / PARTIAL / UNPAID mix -----------
    paid = partial = 0
    for idx, inv in enumerate(invoices):
        # Untouched invoices only, so a re-run never tops up a balance it
        # already part-paid on an earlier run.
        if float(inv["amountPaid"]) > 0:
            continue
        outstanding = float(inv["amount"])
        bucket = idx % 3
        if bucket == 0:
            amount, method = outstanding, "BANK_TRANSFER"
            paid += 1
        elif bucket == 1:
            amount, method = round(outstanding / 2), "CARD"
            partial += 1
        else:
            continue  # leave unpaid
        c.post(f"{base}/invoices/{inv['id']}/payments", {
            "amount": amount,
            "method": method,
            "note": "پرداخت نمایشی",
        })
    # Report the resulting state rather than this run's actions, so the line
    # is meaningful on a re-run that recorded nothing new.
    tally = {}
    for inv in (c.get(f"{base}/invoices") or []):
        tally[inv["status"]] = tally.get(inv["status"], 0) + 1
    step("invoice status: " + ", ".join(f"{v} {k}" for k, v in sorted(tally.items())))

    # -- grades -------------------------------------------------------------
    print("\nGrades")
    by_class = {}
    for s in students:
        by_class.setdefault(s["classId"], []).append(s)
    count = 0
    for klass in classes:
        for s in by_class.get(klass["id"], []):
            level = random.choices(DESCRIPTIVE_LEVELS, DESCRIPTIVE_WEIGHTS)[0]
            c.post(f"{base}/grades", {
                "studentId": s["studentId"],
                "classId": klass["id"],
                "academicYear": args.academic_year,
                "term": "FIRST",
                "descriptiveLevel": level,
                "numericScore": None,
                "comment": None,
            })
            count += 1
    step(f"{count} grades recorded for نوبت اول {args.academic_year}")

    # -- paid modules -------------------------------------------------------
    print("\nCurriculum & exams (paid modules)")
    try:
        c.get(f"{base}/topics")
    except ApiError as e:
        if e.problem_type == "module-not-entitled":
            print("\n  Skipped — this school does not have the CURRICULUM /")
            print("  STUDY_PLANNER / EXAM modules. They are paid plans and")
            print("  cannot be granted over HTTP by design.\n")
            print("  Run this SQL, then re-run this script:\n")
            print(GRANT_PAID_MODULES_SQL.format(school_id=sid))
            print()
            summary(args, sid, students, classes)
            return
        raise

    existing_topics = {t["name"]: t for t in (c.get(f"{base}/topics") or [])}
    topic_ids = {}
    for order, (parent_name, children) in enumerate(TOPICS, start=1):
        if parent_name in existing_topics:
            parent = existing_topics[parent_name]
        else:
            parent = c.post(f"{base}/topics", {
                "parentId": None, "name": parent_name, "sortOrder": order})
        topic_ids[parent_name] = parent["id"]
        for child_order, child_name in enumerate(children, start=1):
            if child_name in existing_topics:
                child = existing_topics[child_name]
            else:
                child = c.post(f"{base}/topics", {
                    "parentId": parent["id"],
                    "name": child_name,
                    "sortOrder": child_order,
                })
            topic_ids[child_name] = child["id"]
    step(f"{len(topic_ids)} topics")

    existing_q = {q["text"] for q in (c.get(f"{base}/questions") or [])}
    added = 0
    for topic_name, items in QUESTIONS.items():
        tid = topic_ids.get(topic_name)
        if tid is None:
            continue
        for text, choices, correct, answer in items:
            if text in existing_q:
                continue
            c.post(f"{base}/questions", {
                "topicId": tid,
                "text": text,
                "choice1": choices[0],
                "choice2": choices[1],
                "choice3": choices[2],
                "choice4": choices[3],
                "correctChoice": correct,
                "answer": answer,
                "difficulty": random.choice(["EASY", "MEDIUM", "HARD"]),
                "discriminationIndex": None,
            })
            added += 1
    step(f"{added} questions added")

    # A collective exam starting shortly, so it is live during a demo.
    title = f"آزمون ریاضی - نوبت اول {args.academic_year}"
    existing_exam = next((e for e in (c.get(f"{base}/exams") or [])
                          if e.get("title") == title), None)
    if existing_exam:
        step(f"exam «{title}» already exists")
    else:
        start = (datetime.now() + timedelta(minutes=5)).replace(second=0, microsecond=0)
        end = start + timedelta(days=7)
        exam = c.post(f"{base}/exams", {
            "title": title,
            "start": start.isoformat(timespec="seconds"),
            "end": end.isoformat(timespec="seconds"),
            "durationMinutes": 40,
        })
        c.post(f"{base}/exams/{exam['id']}/assign",
               {"studentIds": [s["studentId"] for s in students[:12]]})
        try:
            c.post(f"{base}/exams/{exam['id']}/sections/auto", {
                "topicId": topic_ids["ریاضی"],
                "name": "ریاضی",
                "count": 5,
                "lazy": True,
            })
        except ApiError as e:
            if e.problem_type != "insufficient-questions":
                raise
        step(f"exam «{title}» created, 12 students assigned, "
             f"opens {start.strftime('%Y-%m-%d %H:%M')}")

    summary(args, sid, students, classes)


def summary(args, school_id, students, classes):
    url = args.url.rstrip("/")
    print("\n" + "=" * 62)
    print("Done.")
    print("=" * 62)
    print(f"  Sign in at   {url}/login")
    print(f"  Email        {args.email}")
    print(f"  Password     (the one you passed on the command line)")
    print(f"  School       #{school_id} — {len(classes)} classes, "
          f"{len(students)} students")
    print(f"  Entry page   {url}/s/{args.slug}")
    print()
    print("  Worth showing: /people /classes /students /finance /grades")
    print()
    print("  This is an ordinary account, not a seeded one — change its")
    print("  password or delete the school when the presentation is over.")
    print()


if __name__ == "__main__":
    try:
        main()
    except ApiError as e:
        print(f"\nFailed: {e}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
