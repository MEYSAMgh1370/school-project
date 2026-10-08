<%-- آیکن (DESIGN.md §۵b). مجموعه‌ی کوچکِ vendored از inline SVG: بدون icon pack،
     بدون WebJar، بدون CDN — مسیرها همین‌جا نوشته شده‌اند، مثل تصویرهای
     empty-state.

     پارامترها:
       name — یکی از: chevron | calendar | clock | people | class | exam |
              finance | check | grades | share. هر آیکنِ تازه یک خط در
              DESIGN.md §۵b می‌خواهد؛ چیزی «محض احتیاط» اضافه نمی‌شود.
       size — ۱۶، ۲۰ یا ۲۴ (px)

     شبکه‌ی ۲۴×۲۴، خط ۲px، سر و اتصال گرد، `currentColor`، بدون پُرکردن. آیکن
     همیشه کنار برچسب متنی می‌آید، پس `aria-hidden` است؛ فقط back/menu/close
     تنها می‌آیند و آن‌ها `aria-label` روی دکمه‌شان دارند. آیکنِ جهت‌دار
     (chevron) به قرارداد RTL کشیده شده — به inline-end اشاره می‌کند — و
     `.app-dir-glyph` در سند LTR آن را آینه می‌کند. --%>
@param String name
@param int size = 20

<svg class="app-icon@if("chevron".equals(name)) app-dir-glyph@endif" viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    @if("chevron".equals(name))
        <path d="m15 18-6-6 6-6"/>
    @elseif("calendar".equals(name))
        <rect x="3" y="5" width="18" height="16" rx="3"/>
        <path d="M8 3v4M16 3v4M3 10h18"/>
    @elseif("clock".equals(name))
        <circle cx="12" cy="12" r="9"/>
        <path d="M12 7v5l3 2"/>
    @elseif("people".equals(name))
        <circle cx="9" cy="8" r="3.5"/>
        <path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/>
        <path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 14.3c1.8.8 3 2.7 3 5.7"/>
    @elseif("class".equals(name))
        <path d="M4 11 12 4l8 7v9H4z"/>
        <path d="M10 20v-5h4v5"/>
    @elseif("exam".equals(name))
        <rect x="5" y="4" width="14" height="17" rx="3"/>
        <path d="M9 4h6v3H9zM9 14l2 2 4-4"/>
    @elseif("finance".equals(name))
        <rect x="3" y="6" width="18" height="13" rx="3"/>
        <path d="M3 11h18M7 15h4"/>
    @elseif("check".equals(name))
        <path d="M20 6 9 17l-5-5"/>
    @elseif("grades".equals(name))
        <path d="M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/>
        <path d="M14 3v5h5M9 13h6M9 17h6"/>
    @elseif("share".equals(name))
        <circle cx="18" cy="5" r="2.5"/>
        <circle cx="6" cy="12" r="2.5"/>
        <circle cx="18" cy="19" r="2.5"/>
        <path d="m8.2 10.8 7.6-4.5M8.2 13.2l7.6 4.5"/>
    @elseif("alert".equals(name))
        <circle cx="12" cy="12" r="9"/>
        <path d="M12 8v5M12 16.5v.5"/>
    @endif
</svg>
