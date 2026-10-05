<%-- صفحه‌ی اول عمومی: بدون navbar مشترک، چون آن به احراز هویت نیاز دارد.
     بازدیدکننده‌ی واردشده در LandingPageController به /dashboard هدایت می‌شود.

     متن این صفحه فقط چیزهایی را می‌گوید که واقعاً ساخته شده‌اند
     (openspec/specs/current-system.md و decisions/2026-07-20-free-tier-
     entitlements.md). هیچ آمار، مشتری نمونه، قیمت یا راه ارتباطی ساختگی
     اینجا نیست، و هر پیوند به مسیری می‌رود که وجود دارد. تنها استثنا
     پیشخوان نمایشیِ هیرو است که صریحاً «نمونه‌ی نمایشی» برچسب خورده. --%>
@template.layout.main(title = "رشدِاندیشه", styles = java.util.List.of("/css/landing.css"), scripts = java.util.List.of("/js/landing.js"), content = @`
<div class="app-landing">
    <header class="app-marketing-header">
        <div class="container app-marketing-header-inner">
            <div class="app-marketing-header-start">
                <a class="app-landing-brand" href="/">
                    <span class="app-landing-brand-mark" aria-hidden="true"></span>
                    <span>رشدِاندیشه</span>
                </a>
                <nav class="app-marketing-nav" aria-label="بخش‌های صفحه">
                    <a href="#landing-features-title">امکانات</a>
                    <a href="#landing-steps-title">مراحل شروع</a>
                    <a href="#landing-faq-title">پرسش‌ها</a>
                </nav>
            </div>
            <nav class="buttons mb-0" aria-label="ناوبری عمومی">
                <a class="button is-light" href="/login">ورود</a>
                <a class="button is-primary app-marketing-header-cta" href="/register?next=/schools/new">شروع رایگان</a>
            </nav>
        </div>
    </header>

    <main id="main-content" tabindex="-1">
        <%-- هیرو (بازطراحی پیشنهادی، ۲۰۲۶-۱۰-۰۵): نشان، تیتر با یک عبارت
             تأکیدی، یک کنش اصلی، سه نکته‌ی اعتماد، و نمونه‌ی نمایشی پیشخوان.
             پیشخوان فقط تصویرسازی است: aria-hidden، با برچسب «نمونه‌ی نمایشی»،
             و اعدادش ساختگی‌اند و ادعای آماری نیستند. --%>
        <section class="section app-landing-hero">
            <div class="container">
                <div class="columns is-variable is-8 is-vcentered">
                    <div class="column is-5-desktop">
                        <span class="tag is-light mb-4">برای مدرسه‌هایی که با دقت رشد می‌کنند</span>
                        <h1 class="title is-4">مدرسه را با یک <span class="app-landing-emphasis">تصویر روشن</span> از امروز و فردا اداره کنید.</h1>
                        <p class="app-landing-intro">
                            رشدِاندیشه، کارهای روزمره‌ی مدرسه را از اعضا و کلاس‌ها تا امور مالی و مسیر آموزشی،
                            در یک فضای منظم کنار هم می‌آورد.
                        </p>
                        <div class="buttons mt-5">
                            <a class="button is-primary is-medium" href="/register?next=/schools/new">شروع رایگان</a>
                            <a class="app-landing-text-link" href="#landing-steps-title">مراحل شروع را ببینید</a>
                        </div>
                        <p class="is-size-7 has-text-grey mt-4">
                            ساخت مدرسه رایگان است؛ بعد از ساخت حساب، مستقیم به ثبت مدرسه می‌روید.
                            حساب دارید؟ <a href="/login?next=/schools/new">ثبت مدرسه</a>
                        </p>
                        <ul class="app-landing-facts">
                            <li>
                                <span class="app-landing-fact-title">یک حساب، چند مدرسه</span>
                                <span class="is-size-7 has-text-grey">نقش جداگانه در هر مدرسه</span>
                            </li>
                            <li>
                                <span class="app-landing-fact-title">نقش‌های روشن</span>
                                <span class="is-size-7 has-text-grey">مدیر، معلم، مالی، دانش‌آموز و والد</span>
                            </li>
                            <li>
                                <span class="app-landing-fact-title">فارسی از پایه</span>
                                <span class="is-size-7 has-text-grey">تقویم جلالی، هفته از شنبه</span>
                            </li>
                        </ul>
                    </div>
                    <div class="column is-7-desktop">
                        <figure class="app-landing-stage" role="img"
                                aria-label="نمونه‌ی نمایشی پیشخوان مدیر در رشدِاندیشه: تعداد دانش‌آموزان، حضور امروز، شهریه‌ی ماه و برنامه‌ی امروز">
                            <div class="app-landing-window" aria-hidden="true">
                                <div class="app-landing-window-bar">
                                    <span class="app-landing-window-dots"><span></span><span></span><span></span></span>
                                    <span class="is-size-7 has-text-grey">پیشخوان مدیر · نمونه‌ی نمایشی</span>
                                </div>
                                <div class="app-landing-window-body">
                                    <div class="app-landing-window-side">
                                        <span class="is-active">پیشخوان</span>
                                        <span>اعضا</span>
                                        <span>کلاس‌ها</span>
                                        <span>امور مالی</span>
                                        <span>آموزش</span>
                                    </div>
                                    <div class="app-landing-window-main">
                                        <div class="app-landing-window-top">
                                            <span class="app-landing-window-school">دبستان مهر</span>
                                            <span class="tag is-light">نقش: مدیر</span>
                                        </div>
                                        <div class="app-landing-stats">
                                            <div class="app-landing-stat">
                                                <span class="is-size-7 has-text-grey">دانش‌آموزان</span>
                                                <strong class="app-landing-stat-value">۴۸۶</strong>
                                                <span class="is-size-7 has-text-grey">در ۱۸ کلاس</span>
                                            </div>
                                            <div class="app-landing-stat">
                                                <span class="is-size-7 has-text-grey">حضور امروز</span>
                                                <strong class="app-landing-stat-value">۹۶٪</strong>
                                                <span class="is-size-7 has-text-grey">۱۹ غیبت ثبت شد</span>
                                            </div>
                                            <div class="app-landing-stat">
                                                <span class="is-size-7 has-text-grey">شهریه‌ی مهر</span>
                                                <strong class="app-landing-stat-value">۷۲٪</strong>
                                                <progress class="progress is-primary app-landing-stat-bar" value="72" max="100"></progress>
                                            </div>
                                        </div>
                                        <div class="app-landing-agenda">
                                            <div class="app-landing-agenda-heading">
                                                <strong>برنامه‌ی امروز</strong>
                                                <span class="is-size-7 has-text-grey">۳ مورد</span>
                                            </div>
                                            <div class="app-landing-agenda-item">
                                                <span class="is-size-7 has-text-grey">۸:۰۰</span>
                                                <span><strong>ریاضی · پنجم الف</strong><span class="is-size-7 has-text-grey">خانم احمدی · کلاس ۲</span></span>
                                            </div>
                                            <div class="app-landing-agenda-item">
                                                <span class="is-size-7 has-text-grey">۱۰:۳۰</span>
                                                <span><strong>آزمون علوم · پایه‌ی ششم</strong><span class="is-size-7 has-text-grey">ثبت نمره تا پنجشنبه</span></span>
                                            </div>
                                            <div class="app-landing-agenda-item">
                                                <span class="is-size-7 has-text-grey">۱۲:۱۵</span>
                                                <span><strong>جلسه‌ی اولیا · پایه‌ی چهارم</strong><span class="is-size-7 has-text-grey">سالن اجتماعات</span></span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </figure>
                    </div>
                </div>
            </div>
        </section>

        <%-- اسلایدر: تنها عنصر تعاملی این صفحه. هر چهار اسلاید در سرور
             رندر می‌شوند و x-cloak ندارند، پس اگر Alpine بالا نیاید همه
             پشت سر هم دیده می‌شوند. --%>
        <section class="section app-landing-section" aria-labelledby="landing-slider-title">
            <div class="container">
                <div class="app-landing-section-heading">
                    <p class="is-size-7 has-text-grey mb-2">یک دور کوتاه در محصول</p>
                    <h2 class="title is-5 mb-0" id="landing-slider-title">بخش‌های رشدِاندیشه، یکی‌یکی</h2>
                </div>

                <div class="app-landing-slider mt-5"
                     x-data="landingSlider(4)"
                     :class="'is-live'"
                     role="region"
                     aria-roledescription="اسلایدر"
                     aria-label="بخش‌های رشدِاندیشه"
                     @mouseenter="pause()"
                     @focusin="pause()"
                     @keydown.arrow-left.prevent="next()"
                     @keydown.arrow-right.prevent="previous()">

                    <div class="box app-landing-slider-viewport" :aria-live="playing ? 'off' : 'polite'">
                        <article class="app-landing-slide" x-show="active === 0"
                                 role="group" aria-roledescription="اسلاید" aria-label="۱ از ۴">
                            <p class="is-size-7 has-text-grey mb-2">افراد و نقش‌ها</p>
                            <h3 class="title is-6">هر کس سر جای خودش</h3>
                            <p>عضوها را دعوت کنید، نقششان را مشخص کنید و کلاس‌ها و ثبت‌نام دانش‌آموزان را سامان دهید.</p>
                            <ul class="app-landing-slide-list">
                                <li>دعوت عضو و فعال‌سازی حساب</li>
                                <li>نقش مدیر، معلم، مسئول مالی، دانش‌آموز و والد</li>
                                <li>کلاس‌ها و ثبت‌نام دانش‌آموزان</li>
                            </ul>
                        </article>

                        <article class="app-landing-slide" x-show="active === 1"
                                 role="group" aria-roledescription="اسلاید" aria-label="۲ از ۴">
                            <p class="is-size-7 has-text-grey mb-2">امور مالی</p>
                            <h3 class="title is-6">پول مدرسه، بدون پراکندگی</h3>
                            <p>شهریه و صورت‌حساب‌ها، پرداخت‌ها و هزینه‌ها در یک جا ثبت می‌شوند و کنار هم دیده می‌شوند.</p>
                            <ul class="app-landing-slide-list">
                                <li>صورت‌حساب و پیگیری پرداخت</li>
                                <li>هزینه‌ها، طرف‌حساب‌ها و پیوست سند</li>
                                <li>گزارش دوره‌ای بر پایه‌ی تقویم جلالی</li>
                            </ul>
                        </article>

                        <article class="app-landing-slide" x-show="active === 2"
                                 role="group" aria-roledescription="اسلاید" aria-label="۳ از ۴">
                            <p class="is-size-7 has-text-grey mb-2">آموزش و ارزیابی</p>
                            <h3 class="title is-6">از برنامه تا نمره</h3>
                            <p>برنامه‌ی درسی، برنامه‌ی مطالعه، آزمون‌ها و نمره‌ها در امتداد هم قرار می‌گیرند.</p>
                            <ul class="app-landing-slide-list">
                                <li>سرفصل‌های درسی و برنامه‌ی مطالعه</li>
                                <li>آزمون و برگزاری آن برای دانش‌آموز</li>
                                <li>ثبت نمره و دیدن روند آن</li>
                            </ul>
                        </article>

                        <article class="app-landing-slide" x-show="active === 3"
                                 role="group" aria-roledescription="اسلاید" aria-label="۴ از ۴">
                            <p class="is-size-7 has-text-grey mb-2">دانش‌آموز و والد</p>
                            <h3 class="title is-6">خانواده هم در جریان است</h3>
                            <p>دانش‌آموز و والد صفحه‌های خودشان را دارند و فقط اطلاعات خودشان را می‌بینند.</p>
                            <ul class="app-landing-slide-list">
                                <li>نمره‌ها و صورت‌حساب‌های خود دانش‌آموز</li>
                                <li>آزمون‌های پیشِ رو</li>
                                <li>برنامه‌ی مطالعه‌ی هفتگی</li>
                            </ul>
                        </article>
                    </div>

                    <div class="app-landing-slider-controls">
                        <button class="button is-light" type="button" aria-label="اسلاید پیشین" @click="previous()">پیشین</button>
                        <div class="app-landing-slider-dots" role="group" aria-label="رفتن به اسلاید">
                            <button class="app-landing-slider-dot" type="button" aria-label="اسلاید ۱"
                                    :aria-current="active === 0" :class="active === 0 ? 'is-active' : ''" @click="select(0)"></button>
                            <button class="app-landing-slider-dot" type="button" aria-label="اسلاید ۲"
                                    :aria-current="active === 1" :class="active === 1 ? 'is-active' : ''" @click="select(1)"></button>
                            <button class="app-landing-slider-dot" type="button" aria-label="اسلاید ۳"
                                    :aria-current="active === 2" :class="active === 2 ? 'is-active' : ''" @click="select(2)"></button>
                            <button class="app-landing-slider-dot" type="button" aria-label="اسلاید ۴"
                                    :aria-current="active === 3" :class="active === 3 ? 'is-active' : ''" @click="select(3)"></button>
                        </div>
                        <button class="button is-light" type="button" aria-label="اسلاید بعدی" @click="next()">بعدی</button>
                    </div>
                </div>
            </div>
        </section>

        <section class="section app-landing-section" aria-labelledby="landing-features-title">
            <div class="container">
                <div class="app-landing-section-heading">
                    <p class="is-size-7 has-text-grey mb-2">هر بخش، در جای خودش</p>
                    <h2 class="title is-5 mb-0" id="landing-features-title">کارهای مهم مدرسه، ساده‌تر و هماهنگ‌تر</h2>
                </div>
                <div class="columns is-variable is-5 mt-5">
                    <div class="column">
                        <article class="card app-landing-feature-card">
                            <div class="card-content">
                                <h3 class="title is-6">افراد و کلاس‌ها</h3>
                                <p>عضوها، نقش‌ها، کلاس‌ها و ثبت‌نام دانش‌آموزان را با ساختاری روشن مدیریت کنید.</p>
                            </div>
                        </article>
                    </div>
                    <div class="column">
                        <article class="card app-landing-feature-card">
                            <div class="card-content">
                                <h3 class="title is-6">امور مالی شفاف</h3>
                                <p>شهریه، پرداخت، هزینه و گزارش‌های مالی را بدون پراکندگی پیگیری کنید.</p>
                            </div>
                        </article>
                    </div>
                    <div class="column">
                        <article class="card app-landing-feature-card">
                            <div class="card-content">
                                <h3 class="title is-6">پیگیری آموزش</h3>
                                <p>برنامه‌ی درسی، آزمون‌ها، نمره‌ها و برنامه‌های مطالعه را در امتداد هم ببینید.</p>
                            </div>
                        </article>
                    </div>
                </div>
            </div>
        </section>

        <section class="section app-landing-section" aria-labelledby="landing-steps-title">
            <div class="container">
                <div class="columns is-variable is-8 is-vcentered">
                    <div class="column is-4-desktop">
                        <p class="is-size-7 has-text-grey mb-2">شروع کردن، مرحله‌به‌مرحله</p>
                        <h2 class="title is-5" id="landing-steps-title">از ایده تا مدرسه‌ی آماده برای کار</h2>
                        <p>مسیر آغاز کار کوتاه است و هر مرحله، شما را به گام بعدی می‌رساند.</p>
                    </div>
                    <div class="column is-8-desktop">
                        <ol class="app-landing-steps">
                            <li>
                                <span class="app-landing-step-number">۱</span>
                                <div>
                                    <h3 class="title is-6 mb-2">حساب کاربری بسازید</h3>
                                    <p>با یک حساب کاربری، آماده‌ی ساخت یا پیوستن به مدرسه می‌شوید.</p>
                                </div>
                            </li>
                            <li>
                                <span class="app-landing-step-number">۲</span>
                                <div>
                                    <h3 class="title is-6 mb-2">مدرسه‌تان را ثبت کنید</h3>
                                    <p>نام و اطلاعات پایه‌ی مدرسه را وارد کنید تا فضای کاری‌تان آماده شود.</p>
                                </div>
                            </li>
                            <li>
                                <span class="app-landing-step-number">۳</span>
                                <div>
                                    <h3 class="title is-6 mb-2">اعضا و کلاس‌ها را سامان دهید</h3>
                                    <p>همکاران، دانش‌آموزان و کلاس‌ها را اضافه کنید و کار روزانه را شروع کنید.</p>
                                </div>
                            </li>
                        </ol>
                    </div>
                </div>
            </div>
        </section>

        <%-- پاسخ‌ها با آنچه امروز ساخته شده خوانده می‌شوند، از جمله آنچه
             هنوز نیست (openspec/specs/current-system.md §Current Limitations). --%>
        <section class="section app-landing-section" aria-labelledby="landing-faq-title">
            <div class="container">
                <div class="app-landing-section-heading">
                    <p class="is-size-7 has-text-grey mb-2">پیش از شروع</p>
                    <h2 class="title is-5 mb-0" id="landing-faq-title">چند پرسش که معمولاً می‌پرسند</h2>
                </div>
                <div class="app-landing-faq mt-5">
                    <article class="box app-landing-faq-item">
                        <h3 class="title is-6">شروع کار چه هزینه‌ای دارد؟</h3>
                        <p>ساخت مدرسه رایگان است و بخش‌های «آموزش» و «مالی» از همان ابتدا فعال‌اند. بخش‌های برنامه‌ی درسی، برنامه‌ی مطالعه و آزمون در این نسخه به‌صورت خودکار فعال نمی‌شوند.</p>
                    </article>
                    <article class="box app-landing-faq-item">
                        <h3 class="title is-6">با یک حساب، چند مدرسه؟</h3>
                        <p>به تعداد دلخواه. نقش شما در هر مدرسه جداگانه است و از نوار بالای صفحه بین مدرسه‌ها جابه‌جا می‌شوید.</p>
                    </article>
                    <article class="box app-landing-faq-item">
                        <h3 class="title is-6">دانش‌آموز و والد هم وارد می‌شوند؟</h3>
                        <p>بله. هرکدام صفحه‌های خودشان را دارند و تنها نمره، صورت‌حساب، آزمون و برنامه‌ی مطالعه‌ی خودشان را می‌بینند.</p>
                    </article>
                    <article class="box app-landing-faq-item">
                        <h3 class="title is-6">تاریخ‌ها و اعداد چطور نمایش داده می‌شوند؟</h3>
                        <p>تقویم جلالی و اعداد فارسی، و هفته از شنبه آغاز می‌شود.</p>
                    </article>
                </div>
            </div>
        </section>

        <section class="section">
            <div class="container">
                <div class="box app-landing-cta">
                    <div>
                        <h2 class="title is-5">برای یک شروع منظم آماده‌اید؟</h2>
                        <p>مدرسه‌تان را ثبت کنید و فضای کاری‌تان را گام‌به‌گام بسازید.</p>
                    </div>
                    <a class="button is-primary" href="/login?next=/schools/new">ثبت مدرسه</a>
                </div>
            </div>
        </section>
    </main>

    <footer class="app-landing-footer">
        <div class="container">
            <div class="app-landing-footer-columns">
                <div class="app-landing-footer-brand">
                    <a class="app-landing-brand" href="/">رشدِاندیشه</a>
                    <p class="is-size-7 has-text-grey mt-2">
                        سامانه‌ی مدیریت مدرسه: اعضا و کلاس‌ها، امور مالی و مسیر آموزشی، در یک فضای منظم.
                    </p>
                </div>
                <nav class="app-landing-footer-column" aria-labelledby="landing-footer-start">
                    <h2 class="is-size-7 has-text-grey" id="landing-footer-start">شروع</h2>
                    <ul>
                        <li><a href="/login?next=/schools/new">ثبت مدرسه</a></li>
                        <li><a href="/register?next=/schools/new">ایجاد حساب کاربری</a></li>
                        <li><a href="/login">ورود</a></li>
                    </ul>
                </nav>
                <nav class="app-landing-footer-column" aria-labelledby="landing-footer-onpage">
                    <h2 class="is-size-7 has-text-grey" id="landing-footer-onpage">در این صفحه</h2>
                    <ul>
                        <li><a href="#landing-slider-title">بخش‌های محصول</a></li>
                        <li><a href="#landing-features-title">کارهای مدرسه</a></li>
                        <li><a href="#landing-steps-title">مراحل شروع</a></li>
                        <li><a href="#landing-faq-title">پرسش‌های رایج</a></li>
                    </ul>
                </nav>
                <div class="app-landing-footer-column">
                    <h2 class="is-size-7 has-text-grey">یادداشت</h2>
                    <p class="is-size-7 has-text-grey">
                        رشدِاندیشه در حال ساخته‌شدن است و بخش‌های تازه به‌مرور اضافه می‌شوند.
                    </p>
                </div>
            </div>
            <div class="app-landing-footer-inner">
                <span class="is-size-7 has-text-grey">رشدِاندیشه؛ همراه مدیریت روزمره‌ی مدرسه</span>
                <a class="is-size-7 has-text-grey-dark" href="/login">ورود به حساب کاربری</a>
            </div>
        </div>
    </footer>
</div>
`)
