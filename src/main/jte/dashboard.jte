@import com.example.roshdeandishe.web.DashboardPageController.DashboardModel
@import com.example.roshdeandishe.web.NavbarModel

@param DashboardModel model
@param NavbarModel navbar

@template.layout.main(title = "داشبورد", scripts = java.util.List.of("/js/navbar.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section">
    <div class="container">
        <h1 class="title is-4">عضویت‌های من</h1>
        <p class="subtitle is-6" dir="ltr">${model.email()}</p>

        @if(model.memberships().isEmpty())
            <div class="notification">
                هنوز در هیچ مدرسه‌ای عضویتی ندارید.
            </div>
        @else
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
                        <td>${membership.roleCode()}</td>
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
        @endif
    </div>
</section>
`)
