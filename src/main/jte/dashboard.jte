@import com.example.roshdeandishe.iam.shared.DashboardPageController.DashboardModel

@param DashboardModel model

<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>داشبورد - رشدِاندیشه</title>
    <link rel="stylesheet" href="/webjars/bulma/1.0.4/css/bulma.min.css">
    <script defer src="/webjars/alpinejs/3.15.12/dist/cdn.min.js"></script>
</head>
<body>
<nav class="navbar is-link" role="navigation" aria-label="main navigation"
     x-data="{
        async logout() {
            try {
                await fetch('/api/auth/logout', { method: 'POST' });
            } finally {
                window.location.href = '/login';
            }
        }
     }">
    <div class="navbar-brand">
        <span class="navbar-item has-text-weight-bold">رشدِاندیشه</span>
    </div>
    <div class="navbar-end">
        <div class="navbar-item">${model.fullName()}</div>
        <div class="navbar-item">
            <button class="button is-light" @click="logout">خروج</button>
        </div>
    </div>
</nav>

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
                    <th>شناسه مدرسه</th>
                    <th>نقش</th>
                    <th>وضعیت</th>
                </tr>
                </thead>
                <tbody>
                @for(var membership : model.memberships())
                    <tr>
                        <td>${membership.schoolId()}</td>
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
</body>
</html>
