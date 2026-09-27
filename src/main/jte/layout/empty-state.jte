<%-- حالت خالی (DESIGN.md §۵ و §۵a).
     پانزده جای کد همین شکل را تکرار می‌کردند: یک `.notification` با یک
     جمله‌ی فارسی که وقتی مجموعه خالی است نشان داده می‌شود.

     پارامترها:
       show    — همان عبارتِ Alpine که قبلاً در x-show بود (بدون تغییر منتقل شود)
       message — متن؛ Content است تا لینک و span شرطی هم بتواند داشته باشد
       art     — نامِ تصویر: list | people | money | exam | calendar | none
       tone    — "" یا "is-light"، هرچه صفحه از قبل داشت

     تصویرها عمداً inline SVG و دو-رنگ‌اند (خط به رنگ primary روی پُرکردنِ
     primary-soft): §۱۱.۳ هنوز هیچ icon pack را نمی‌پذیرد و این‌ها هم pack
     نیستند — چند شکلِ دست‌چین که فقط همین‌جا استفاده می‌شوند. --%>
@param String show
@param gg.jte.Content message
@param String art = "list"
@param String tone = ""

<div class="notification app-empty ${tone}" x-show="${show}">
    @if(!"none".equals(art))
        <span class="app-empty__art" aria-hidden="true">
            @if("people".equals(art))
                <svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="18" cy="17" r="6"/>
                    <path d="M8 38c0-5.5 4.5-10 10-10s10 4.5 10 10"/>
                    <circle cx="33" cy="20" r="4.5"/>
                    <path d="M33 29c4.4 0 8 3.6 8 8"/>
                </svg>
            @elseif("money".equals(art))
                <svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="7" y="13" width="34" height="22" rx="3"/>
                    <circle cx="24" cy="24" r="5"/>
                    <path d="M13 19v10M35 19v10"/>
                </svg>
            @elseif("exam".equals(art))
                <svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 7h17l8 8v26a2 2 0 0 1-2 2H12a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z"/>
                    <path d="M28 7v9h9"/>
                    <path d="M17 27l3.5 3.5L27 24"/>
                </svg>
            @elseif("calendar".equals(art))
                <svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="8" y="12" width="32" height="28" rx="3"/>
                    <path d="M8 21h32M17 12V8M31 12V8"/>
                    <path d="M16 29h5M27 29h5"/>
                </svg>
            @else
                <svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="10" width="30" height="28" rx="3"/>
                    <path d="M16 19h16M16 25h16M16 31h9"/>
                </svg>
            @endif
        </span>
    @endif
    <span class="app-empty__text">${message}</span>
</div>
