<%-- تنها جای مدیریت <head> (استایل‌شیت‌ها و اسکریپت‌ها): هر صفحه فقط
     عنوان و محتوای body را می‌دهد. صفحه‌ای که اسکریپت اختصاصی دارد
     (کامپوننت Alpine.data استاتیک - قانون جای‌گذاری کد در CLAUDE.md §2)
     آن را با scripts می‌دهد؛ این تگ‌ها عمداً *قبل از* اسکریپت Alpine
     می‌آیند: هر دو defer هستند و به ترتیب سند اجرا می‌شوند، و ثبت
     Alpine.data باید پیش از رویداد alpine:init انجام شده باشد.
     styles همان الگو برای CSS اختصاصی صفحه است (DESIGN.md: کاستوم CSS
     همیشه فایل خارجی، هرگز <style> این‌لاین - app.css تنها توکن‌ها/کف
     دسترسی‌پذیری است، نه لایوت اختصاصی یک صفحه مثل گرید تقویمی). بعد از
     app.css می‌آید تا بتواند از توکن‌های آن استفاده کند. --%>
@param String title
@param gg.jte.Content content
@param java.util.List<String> scripts = java.util.List.of()
@param java.util.List<String> styles = java.util.List.of()
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - رشدِاندیشه</title>
    <link rel="stylesheet" href="/webjars/bulma/1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/css/app.css">
    @for(var style : styles)
        <link rel="stylesheet" href="${style}">
    @endfor
    @for(var script : scripts)
        <script defer src="${script}"></script>
    @endfor
    <script defer src="/webjars/alpinejs/3.15.12/dist/cdn.min.js"></script>
</head>
<body>
<a class="app-skip-link" href="#main-content">پرش به محتوای اصلی</a>
${content}
</body>
</html>
