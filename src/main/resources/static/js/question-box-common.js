// پایه‌ی مشترک صفحه‌های باکس سؤالات (openspec/changes/question-box.md): فراخوانی REST با الگوی tenant
// (X-School-ID + X-XSRF-TOKEN)، دسته‌بندی خطا و دریافت تصویر. تگ img نمی‌تواند هدر بفرستد، پس تصویر
// با fetch گرفته می‌شود و به blob-URL تبدیل می‌شود (دایرکتیو x-qbimg).
(function () {
    const imageCache = new Map();

    const QbApi = {
        schoolId() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            return Number.isInteger(saved) ? saved : null;
        },

        csrf() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        // path نسبی به /api/admin/schools/{id}/question-box است، مگر با /api/ شروع شود (مثلاً بانک سؤال).
        async call(schoolId, method, path, options = {}) {
            const url = path.startsWith('/api/') ? path : `/api/admin/schools/${schoolId}/question-box${path}`;
            const headers = { 'X-School-ID': schoolId };
            if (method !== 'GET') {
                headers['X-XSRF-TOKEN'] = this.csrf();
            }
            let body;
            if (options.form) {
                body = options.form;
            } else if (options.json !== undefined) {
                headers['Content-Type'] = 'application/json';
                body = JSON.stringify(options.json);
            }
            let response;
            try {
                response = await fetch(url, { method, headers, body });
            } catch (e) {
                return { ok: false, status: 0, blocking: false, message: 'ارتباط با سرور برقرار نشد.' };
            }
            if (!response.ok) {
                return { ok: false, status: response.status, ...(await this.classify(response)) };
            }
            const type = response.headers.get('content-type') || '';
            return {
                ok: true,
                status: response.status,
                data: response.status !== 204 && type.includes('json') ? await response.json() : null
            };
        },

        async classify(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول مورد نیاز را ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به این بخش را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        // kind: 'question' | 'header'
        imageUrl(kind, id) {
            const schoolId = this.schoolId();
            const key = `${schoolId}:${kind}:${id}`;
            if (!imageCache.has(key)) {
                const path = kind === 'header' ? `/headers/${id}/image` : `/images/${id}`;
                imageCache.set(key, fetch(`/api/admin/schools/${schoolId}/question-box${path}`, {
                    headers: { 'X-School-ID': schoolId }
                }).then(r => (r.ok ? r.blob() : null))
                    .then(blob => (blob ? URL.createObjectURL(blob) : null))
                    .catch(() => null));
            }
            return imageCache.get(key);
        },

        forgetImage(kind, id) {
            imageCache.delete(`${this.schoolId()}:${kind}:${id}`);
        },

        digits(value) {
            return window.Calendar ? window.Calendar.digits(value) : String(value);
        }
    };

    window.QbApi = QbApi;

    document.addEventListener('alpine:init', () => {
        // <img x-qbimg="{ kind: 'question', id: image.id }" alt="…">
        Alpine.directive('qbimg', (el, { expression }, { evaluate, effect }) => {
            effect(async () => {
                const spec = evaluate(expression);
                if (!spec || !spec.id) {
                    el.removeAttribute('src');
                    return;
                }
                const url = await QbApi.imageUrl(spec.kind, spec.id);
                if (url) {
                    el.src = url;
                }
            });
        });
    });
})();
