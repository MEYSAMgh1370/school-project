<%-- تنها جای مدیریت <head> (استایل‌شیت‌ها و اسکریپت‌ها): هر صفحه فقط
     عنوان و محتوای body را می‌دهد. اگر روزی صفحه‌ای اسکریپت اختصاصی
     (فایل استاتیک) لازم داشت، پارامتر جدیدی برای آن به همین layout اضافه
     کنید - نه یک <head> تکراری در آن صفحه. --%>
@param String title
@param gg.jte.Content content
@param java.util.List<String> scripts = java.util.List.of()
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - رشدِاندیشه</title>
    <link rel="stylesheet" href="/webjars/bulma/1.0.4/css/bulma.min.css">
    <%-- Alpine.data() باید قبل از شروع خودِ Alpine ثبت شود؛ چون readyState
         در لحظه‌ی اجرای اسکریپت‌های defer از قبل "interactive" است، Alpine
         بلافاصله بعد از اجرای اسکریپت خودش شروع می‌شود - پس اسکریپت‌های
         Alpine.data صفحه باید قبل از تگ اسکریپت خودِ Alpine بیایند، نه بعدش. --%>
    @for(var script : scripts)
        <script defer src="${script}"></script>
    @endfor
    <script defer src="/webjars/alpinejs/3.15.12/dist/cdn.min.js"></script>
</head>
<body>
${content}
</body>
</html>
