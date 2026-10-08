<%-- پوسته‌ی صفحه‌های وابسته به مدرسه (DESIGN.md §۵).
     چهارده صفحه دقیقاً همین سه چیز را تکرار می‌کردند: هشدارِ «مدرسه‌ای
     انتخاب نشده»، ناحیه‌ی خطا، و ناحیه‌ی پیام موفقیت. تنها متنِ هشدار
     فرق داشت، پس همان یک پارامتر است.

     صفحه‌هایی که فقط خواندنی‌اند (کارنامه، فاکتورها، آزمون‌های من) در
     کامپوننت Alpine خود `notice` ندارند؛ برای آن‌ها successNotice = false
     بدهید وگرنه Alpine روی متغیرِ تعریف‌نشده خطا می‌دهد. --%>
@param String noSchoolMessage
@param gg.jte.Content content
@param boolean successNotice = true

<div class="notification is-warning" x-cloak x-show="!schoolId">
    ${noSchoolMessage}
</div>

<template x-if="schoolId">
    <div>
        <div class="notification is-danger" role="alert" x-show="error">
            <span class="app-notice">@template.layout.icon(name = "alert", size = 20)<span x-text="error"></span></span>
        </div>
        @if(successNotice)
            <div class="notification is-success" role="status" x-show="notice" x-text="notice"></div>
        @endif
        ${content}
    </div>
</template>
