<%-- صفحه‌ی اول عمومی: بدون navbar مشترک، چون آن به احراز هویت نیاز دارد.
     بازدیدکننده‌ی واردشده در LandingPageController به /dashboard هدایت می‌شود. --%>
@template.layout.main(title = "رشدِاندیشه", styles = java.util.List.of("/css/landing.css"), content = @`
<div class="app-landing">
    <header class="app-marketing-header">
        <div class="container app-marketing-header-inner">
            <a class="app-landing-brand" href="/">رشدِاندیشه</a>
            <nav class="buttons mb-0" aria-label="ناوبری عمومی">
                <a class="button is-light" href="/login">ورود</a>
                <a class="button is-light" href="/register">ایجاد حساب کاربری</a>
            </nav>
        </div>
    </header>

    <main id="main-content" tabindex="-1">
        <section class="section app-landing-hero">
            <div class="container">
                <div class="columns is-variable is-8 is-vcentered">
                    <div class="column is-7-desktop">
                        <span class="tag is-light mb-4">برای مدرسه‌هایی که با دقت رشد می‌کنند</span>
                        <h1 class="title is-4">مدرسه را با یک تصویر روشن از امروز و فردا اداره کنید.</h1>
                        <p class="app-landing-intro">
                            رشدِاندیشه، کارهای روزمره‌ی مدرسه را از اعضا و کلاس‌ها تا امور مالی و مسیر آموزشی،
                            در یک فضای منظم کنار هم می‌آورد.
                        </p>
                        <div class="buttons mt-5">
                            <a class="button is-primary" href="/login?next=/schools/new">ثبت مدرسه</a>
                            <a class="button is-light" href="/register?next=/schools/new">ابتدا حساب کاربری بسازید</a>
                        </div>
                        <p class="is-size-7 has-text-grey mt-4">
                            از همین‌جا شروع کنید؛ پس از ورود، می‌توانید مدرسه‌تان را ثبت و کار را ادامه دهید.
                        </p>
                    </div>
                    <div class="column is-5-desktop">
                        <aside class="box app-landing-preview" aria-label="نمایی از بخش‌های رشدِاندیشه">
                            <div class="app-landing-preview-heading">
                                <div>
                                    <p class="is-size-7 has-text-grey mb-1">یک نمای ساده و قابل پیگیری</p>
                                    <h2 class="title is-6 mb-0">امروز در مدرسه</h2>
                                </div>
                                <span class="tag is-light">مرتب</span>
                            </div>
                            <div class="app-landing-preview-item">
                                <p class="is-size-7 has-text-grey mb-1">اعضا و کلاس‌ها</p>
                                <p>نقش‌ها، دانش‌آموزان و کلاس‌ها در دسترس‌اند.</p>
                            </div>
                            <div class="app-landing-preview-item">
                                <p class="is-size-7 has-text-grey mb-1">امور مالی</p>
                                <p>شهریه، پرداخت‌ها و هزینه‌ها را کنار هم ببینید.</p>
                            </div>
                            <div class="app-landing-preview-item">
                                <p class="is-size-7 has-text-grey mb-1">مسیر آموزشی</p>
                                <p>برنامه، آزمون و نمره‌ها برای پیگیری آماده‌اند.</p>
                            </div>
                        </aside>
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
        <div class="container app-landing-footer-inner">
            <span class="is-size-7 has-text-grey">رشدِاندیشه؛ همراه مدیریت روزمره‌ی مدرسه</span>
            <a class="is-size-7 has-text-grey-dark" href="/login">ورود به حساب کاربری</a>
        </div>
    </footer>
</div>
`)
