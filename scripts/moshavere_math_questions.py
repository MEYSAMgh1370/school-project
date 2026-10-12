"""Maths questions from the gosaste-4 sheet (combinatorics, number theory, algebra).

Reviewed by hand, one entry per question. The keys were checked by enumeration
where the question is computable (see the notes). Questions 11, 12 and 23 are
left out: their text cannot be read reliably from the sheet, so they need the
source before import. Question 4 is kept, but its key depends on reading
«حداکثر» as a cap on the 200 respondents; this is noted for the teacher.

Topics are chosen by hand (like D16). Counting and permutations go to the
counting chapter, and divisibility to بخش‌پذیری. Question 7 and question 23's
slot have no exact tree node; they are noted.
"""

COUNTING = "14257"   # فصل 6: شمارش، بدون شمردن
PERMUTATION = "14260"  # درس دوم: جایگشت
DIVISIBILITY = "23459"  # بخش‌پذیری
RATIONAL = "18400"  # معرفی و ساده کردن عبارت‌های گویا (closest node for a rational function)

# (sheet number, text, choices (4), correct choice 1-based, topic code, note or None)
QUESTIONS = [
    (1, "با حروف کلمه‌ی KAMYAB چند رمز عبور ۴ حرفی می‌توان ساخت؟",
     ["۱۴۲", "۱۵۶", "۱۸۰", "۱۹۲"], 4, COUNTING, "key checked: 192"),
    (2, "با حروف کلمه‌ی FARHAD چند رمز عبور ۶ حرفی می‌توان ساخت، به طوری که دو حرف A در کنار هم نباشند؟",
     ["۱۲۰", "۱۸۰", "۲۴۰", "۳۰۰"], 3, PERMUTATION, "key checked: 240"),
    (3, "در یک ساختمان ۶ طبقه، افراد $a$، $b$، $c$، $d$، $e$، $f$ هر کدام در یک طبقه زندگی می‌کنند. "
        "اگر بدانیم واحد $a$ بالاتر از $b$ است، در چند حالت فرد $b$ ساکن طبقه‌ی سوم است؟",
     ["۲۴", "۷۲", "۶۰", "۱۲۰"], 2, COUNTING, "key checked: 3 × 4! = 72"),
    (4, "در یک نظرسنجی که از ۵ سؤال تشکیل شده است، ۲۰۰ نفر شرکت کرده‌اند. اگر پاسخ سؤالات به‌صورت «بله، خیر و نمی‌دانم» "
        "باشد و همه‌ی افراد به همه‌ی سؤالات پاسخ بدهند، حداکثر چند پاسخ‌نامه‌ی متفاوت به‌وجود می‌آید؟",
     ["۲۴۳", "۴³", "۴!×۳!", "۲۰۰"], 4, COUNTING,
     "key depends on reading: 3^5 = 243 sheets exist, but only 200 respondents, so 200 is the cap; teacher to confirm"),
    (5, "اگر $n$ عددی طبیعی باشد، از روابط $a \\mid 4n^2+6$ و $a \\mid 6n^2+1$ چند مقدار طبیعی برای $a$ موجود است؟",
     ["۱", "۲", "۳", "۵"], 1, DIVISIBILITY, "key checked: only a = 1"),
    (6, "حروف کلمه‌ی severe را به چند طریق بدون توجه به مفهوم آن می‌توان کنار هم قرار داد، "
        "به طوری که e ها یک در میان باشند؟",
     ["۶", "۳۶", "۱۲", "۲۴"], 3, COUNTING, "key checked: 2 patterns × 3! = 12"),
    (7, "چند نقطه با مختصات صحیح روی نمودار تابع $y=\\dfrac{4x+1}{x-2}$ در ربع دوم دستگاه مختصات قرار دارد؟",
     ["۱", "۲", "۳", "۴"], 2, RATIONAL, "key checked: (-7, 3) and (-1, 1); topic is the closest match"),
    (8, "در چند جایگشت از حروف AABBCCDD عبارت ABCD ظاهر می‌شود؟",
     ["۱۱۹", "۱۲۰", "۱۱۸", "۱۱۵"], 1, COUNTING, "key checked: 119"),
    (9, "حروف کلمه‌ی EARNEST را به چند طریق می‌توان کنار هم قرار داد، به طوری که حرف N همواره در وسط قرار گیرد؟ "
        "(بدون توجه به مفهوم)",
     ["۱۸۰", "۲۱۶", "۲۴۰", "۳۶۰"], 4, COUNTING, "key checked: 6!/2! = 360"),
    (10, "در اثبات درستی رابطه‌ی $a^2b+b^2a\\ge a+b$ به کمک اثبات بازگشتی به کدام رابطه‌ی بدیهی می‌رسیم؟ "
         "($a$ و $b$ دو عدد حقیقی مثبت هستند.)",
     ["$(a+b)^2\\ge0$", "$(a-b)^2\\ge0$", "$(a-2b)^2\\ge0$", "$(2a-b)^2\\ge0$"], 2, COUNTING,
     "statement as on the sheet; teacher to confirm the inequality itself"),
    (13, "تعداد جواب‌های طبیعی معادله‌ی $x_1+2x_2+x_3=20$ کدام است؟",
     ["۶۴", "۷۲", "۸۱", "۹۳"], 3, COUNTING, "key checked: 81 with positive integers"),
    (14, "چند جایگشت چهار حرفی با حروف کلمه‌ی IRANIAN می‌توان نوشت که دقیقاً دو حرف آن تکراری باشد؟",
     ["۸۰", "۱۰۸", "۱۲۰", "۱۴۴"], 2, COUNTING, "key checked: 108 (one repeated pair)"),
    (15, "از ۱۲ نفر دانش‌آموز نمونه، به چند راه می‌توان سه نفر را جهت مشارکت در سه مورد متمایز در امور مدرسه انتخاب کرد؟",
     ["۱۳۲۰", "۶۶۰", "۳۳۰", "۲۲۰"], 1, PERMUTATION, "key checked: 12 × 11 × 10 = 1320"),
    (16, "باقی‌مانده‌ی تقسیم عدد $13^{50}-11^{50}$ بر عدد $22$ چقدر است؟",
     ["صفر", "۱۱", "۱۰", "۱۲"], 4, DIVISIBILITY, "key checked: 12"),
    (17, "شش رقم ۱، ۳، ۳، ۵، ۵، ۵ را از مقوا بریده در کنار یکدیگر جابه‌جا می‌کنیم. تعداد اعداد شش رقمی متمایز کدام است؟",
     ["۶۰", "۷۲", "۸۰", "۱۲۰"], 1, COUNTING, "key checked: 6!/(2!·3!) = 60"),
    (18, "از یک قطعه مقوا، ارقام ۵، ۳، ۲، ۲، ۲ و ۱ بریده شده است. با جایگشت هر سه رقم دلخواه از آن‌ها، "
         "چند عدد سه‌رقمی می‌توان ساخت؟",
     ["۲۸", "۳۰", "۳۲", "۳۴"], 4, COUNTING, "key checked: 34"),
    (19, "تعداد جایگشت‌های سه‌حرفی انتخاب‌شده از حروف کلمه‌ی DELAVAR کدام است؟",
     ["۱۱۵", "۱۲۵", "۱۳۰", "۱۳۵"], 4, PERMUTATION, "key checked: 135"),
    (20, "اگر $5 \\mid 2n+1$ باشد، عبارت $14n^2+19n+6$ همواره بر کدام عدد زیر بخش‌پذیر است؟ ($n\\in\\mathbb{Z}$)",
     ["۱۰", "۲۵", "۱۵", "۳۰"], 2, DIVISIBILITY, "key checked: 25"),
    (21, "۵ حرف از ۸ حرف کلمه‌ی «KHARAZMI» را با جایگشت‌های متمایز کنار هم قرار می‌دهیم. "
         "تعداد کلماتی ۵ حرفی که هر دو حرف A در آن‌ها موجود باشد، کدام است؟",
     ["۲۰", "۶۰", "۱۲۰۰", "۱۴۴۰"], 3, COUNTING, "key checked: 1200"),
    (22, "چند عدد ۳ رقمی با ارقام غیرتکراری کوچک‌تر از ۸۷۴ وجود دارد؟",
     ["۵۶۴", "۸۴۶", "۵۴۶", "۵۰۴"], 1, COUNTING, "key checked: 564"),
    (24, "اگر $a$ و $b$ دو عدد صحیح فرد باشند، بزرگ‌ترین عددی که $a^4-b^4$ همواره بر آن بخش‌پذیر است کدام است؟",
     ["۸۰", "۴۰", "۹۶", "۱۶"], 4, DIVISIBILITY, "key checked: 16"),
    (25, "اگر $11 \\mid a+3b+k$ و $11 \\mid 5a+4b+3$ باشد، کم‌ترین مقدار طبیعی $k$ کدام است؟ ($a,b\\in\\mathbb{Z}$)",
     ["۵", "۶", "۷", "۸"], 1, DIVISIBILITY, "key checked: 5"),
    (26, "تعداد جایگشت‌های ۴ حرفی از حروف کلمه‌ی SALAMAT که دو حرف آن A باشد، کدام است؟",
     ["۲۴", "۳۶", "۵۶", "۷۲"], 4, COUNTING, "key checked: 72"),
    (27, "اگر $x^3-3x^2-4x$ مضرب ۱۱ باشد، آنگاه مجموع ارقام بزرگ‌ترین عدد طبیعی دو رقمی $x$ کدام است؟",
     ["۱۲", "۱۴", "۱۶", "۱۸"], 4, DIVISIBILITY, "key checked: x = 99, digit sum 18"),
    (28, "با حروف کلمه‌ی ZEMESTAN چند رمز عبور چهار حرفی می‌توان ساخت؟",
     ["۴۸۰", "۱۸۰", "۸۴۰", "۱۰۲۰"], 4, COUNTING, "key checked: 1020"),
    (29, "با حروف کلمه‌ی «گل پیرا» بدون تکرار حروف، چند کلمه‌ی ۶ حرفی می‌توان نوشت که در آن دو حرف «پ» و «ر» "
         "کنار هم نیامده باشند؟",
     ["۳۶۰", "۲۴۰", "۷۲۰", "۴۸۰"], 4, COUNTING, "key checked: 720 − 2·5! = 480"),
    (30, "چند کلمه‌ی ۴ حرفی انگلیسی، صرف‌نظر از معنادار بودن، می‌توان ساخت، به‌طوری‌که از اول به آخر یا از آخر به اول "
         "بخوانیم، یک کلمه بخوانیم (مانند noon)؟",
     ["$26!$", "$26\\times25\\times24\\times23$", "$26\\times25$", "$26^2$"], 4, COUNTING, "key checked: 26²"),
]

# Left out until the source is clear: sheet questions 11, 12 and 23.
EXCLUDED = {11: "expression unreadable on the sheet",
            12: "congruence unreadable on the sheet",
            23: "graph question: no tree node; key not checked"}
