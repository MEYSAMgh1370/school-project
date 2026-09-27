<%-- عنوان صفحه (DESIGN.md §۳: نقشِ «Page title» همیشه `title is-4` و
     فقط یکی در هر صفحه). زیرعنوان اختیاری است و اگر بیاید `subtitle is-6`
     می‌شود — همان چیزی که شش صفحه از قبل دستی می‌نوشتند. --%>
@param String title
@param String subtitle = null

<h1 class="title is-4">${title}</h1>
@if(subtitle != null)
    <p class="subtitle is-6">${subtitle}</p>
@endif
