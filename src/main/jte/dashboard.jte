@import com.example.roshdeandishe.web.DashboardPageController.DashboardModel
@import com.example.roshdeandishe.web.NavbarModel
@import com.example.roshdeandishe.web.RoleLabels

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "داشبورد", scripts = java.util.List.of("/js/navbar.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        <%-- CTA ثبت مدرسه فقط در حالت بدون عضویت (جریان بنیان‌گذار):
             به دانش‌آموز/والدِ دعوت‌شده پیشنهاد «مدرسه بسازید» نمی‌دهیم
             (تصمیم ۲۰۲۶-۰۷-۰۶؛ فرم به /schools/new منتقل شد). --%>
        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید. می‌توانید همین حالا
                <a href="/schools/new">مدرسه‌ی خودتان را ثبت کنید</a>،
                یا منتظر دعوت‌نامه‌ی مدرسه‌تان بمانید.
            </div>
        @else
            <div class="table-container">
            <table class="table is-fullwidth is-striped">
                <thead>
                <tr>
                    <th>مدرسه</th>
                    <th>نقش</th>
                    <th>وضعیت</th>
                </tr>
                </thead>
                <tbody>
                @for(var membership : model.memberships())
                    <tr>
                        <td>${membership.schoolName()}</td>
                        <td>${RoleLabels.persian(membership.roleCode())}</td>
                        <td>
                            @if(membership.active())
                                <span class="tag is-success">فعال</span>
                            @else
                                <span class="tag is-danger">غیرفعال</span>
                            @endif
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
            </div>
        @endif
    </div>
</section>
`)
