<%-- صفحه‌ی اول عمومی - بدون navbar مشترک (navbar احراز هویت لازم دارد؛
     این صفحه فقط به ناشناس‌ها نشان داده می‌شود، کاربر لاگین‌شده به
     /dashboard ری‌دایرکت می‌شود - LandingPageController). --%>
@template.layout.main(title = "خانه", content = @`
<section class="hero is-link is-fullheight">
    <div class="hero-body">
        <div class="container has-text-centered">
            <h1 class="title is-1">رشدِاندیشه</h1>
            <p class="subtitle is-4 mt-3">سامانه‌ی یکپارچه‌ی مدیریت مدرسه - ثبت‌نام، کلاس‌ها، شهریه و ارتباط با خانواده‌ها در یک‌جا</p>

            <div class="buttons is-centered mt-6">
                <a class="button is-white is-medium" href="/login">ورود</a>
                <a class="button is-white is-outlined is-medium" href="/register">ثبت‌نام</a>
            </div>

            <div class="box mt-6" style="max-width: 480px; margin-inline: auto;">
                <%-- has-text-dark لازم است: .title درون hero is-link سفید
                     ارث می‌برد و روی box سفید ناپیدا می‌شود --%>
                <h2 class="title is-5 has-text-dark">مدیر مدرسه هستید؟</h2>
                <p class="mb-4">مدرسه‌تان را در رشدِاندیشه ثبت کنید و مدیریت اعضا و کلاس‌ها را همین امروز شروع کنید.</p>
                <a class="button is-link" href="/schools/new">ثبت مدرسه</a>
            </div>
        </div>
    </div>
</section>
`)
