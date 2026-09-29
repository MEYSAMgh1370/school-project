<%-- درِ ورودی برنددارِ مدرسه (/s/{slug}) - صفحه‌ی عمومی، بدون navbar
     (مثل landing؛ SchoolEntryPageController). کلیک روی CTAها
     selectedSchoolId را در localStorage ست می‌کند تا بعد از ورود، همین
     مدرسه در سوییچر انتخاب‌شده باشد - عمداً روی کلیک نه روی load، تا
     بازدید گذری یک کاربرِ چندمدرسه‌ای انتخاب قبلی‌اش را عوض نکند.
     (navbar.js مقدار را با عضویت‌های واقعی می‌سنجد و در صورت نامعتبر
     بودن به اولین مدرسه‌ی خود کاربر برمی‌گردد.) --%>
@import com.example.roshdeandishe.web.dto.SchoolEntryModel

@param SchoolEntryModel model

@template.layout.main(title = model.found() ? model.schoolName() : "مدرسه یافت نشد", content = @`
<section class="hero is-link is-fullheight" id="main-content" role="main" tabindex="-1">
    <div class="hero-body">
        <div class="container has-text-centered">
            @if(model.found())
                <p class="is-size-7 mb-2">سامانه‌ی مدیریت مدرسه</p>
                <h1 class="title is-1">${model.schoolName()}</h1>

                <div class="buttons is-centered mt-6">
                    @if(model.authenticated())
                        <a class="button is-white is-medium" href="/dashboard"
                           onclick="localStorage.setItem('selectedSchoolId', '${model.schoolId()}')">ادامه به داشبورد</a>
                    @else
                        <a class="button is-white is-medium" href="/login"
                           onclick="localStorage.setItem('selectedSchoolId', '${model.schoolId()}')">ورود</a>
                    @endif
                </div>

                @if(!model.authenticated())
                    <p class="mt-4">
                        <a class="has-text-white" href="/activate"
                           onclick="localStorage.setItem('selectedSchoolId', '${model.schoolId()}')">کد دعوت دارید؟ حساب خود را فعال کنید</a>
                    </p>
                @endif

                <p class="is-size-7 mt-6">
                    <a class="has-text-white" href="/">رشدِاندیشه - سامانه‌ی یکپارچه‌ی مدیریت مدرسه</a>
                </p>
            @else
                <h1 class="title is-2">مدرسه‌ای با این نشانی یافت نشد</h1>
                <p class="subtitle is-5 mt-3">نشانی را بررسی کنید، یا نشانی درست را از مدیر مدرسه‌تان بگیرید.</p>

                <div class="buttons is-centered mt-6">
                    <a class="button is-white is-medium" href="/">صفحه‌ی اصلی رشدِاندیشه</a>
                </div>
            @endif
        </div>
    </div>
</section>
`)
