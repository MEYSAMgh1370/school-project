// fetch مشترکِ صفحه‌های حضور و غیاب و تقویم مدرسه: هدرِ X-School-ID روی هر فراخوانی، X-XSRF-TOKEN روی
// نوشتن، و همان تفکیکِ دولایه‌ی 403 که finance.js/timetable.js دارند (classifyError):
//   - ماژول پس‌گرفته‌شده (module-not-entitled) یا مجوزِ نداشته (permission-denied) → blocking: بدنه‌ی صفحه بسته می‌شود
//   - هر خطای دیگر (از جمله 403ِ attendance-past-edit-not-allowed و attendance-not-your-class) → پیامِ خودِ سرور
//     در ناحیه‌ی خطا، صفحه باز می‌ماند
// خطا Error است با blocking (true/false)؛ نبودِ blocking یعنی خطای شبکه (fetch خودش پرتاب کرد).
window.SchoolApi = (function () {
    function csrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    // messages: { entitled, denied } - متنِ هر دو مسدودسازی که مخصوصِ هر صفحه است
    async function classifyError(response, messages) {
        try {
            const body = await response.json();
            const type = body.type || '';
            if (type.endsWith('module-not-entitled')) {
                return { blocking: true, message: messages.entitled };
            }
            if (type.endsWith('permission-denied')) {
                return { blocking: true, message: messages.denied };
            }
            return { blocking: false, type, message: body.detail || body.title || 'خطایی رخ داد.' };
        } catch (e) {
            return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
        }
    }

    async function request(schoolId, method, url, body, messages) {
        const headers = { 'X-School-ID': schoolId };
        if (method !== 'GET') {
            headers['X-XSRF-TOKEN'] = csrfToken();
        }
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (!response.ok) {
            const classified = await classifyError(response, messages);
            const error = new Error(classified.message);
            error.blocking = classified.blocking;
            // برای صفحه‌هایی که متن را بر اساس نوعِ ProblemDetail خودشان می‌سازند (bale-bot.js)
            error.status = response.status;
            error.type = classified.type || '';
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    // پیامِ خطا برای نمایش: خطای شبکه (بدونِ blocking) متنِ ثابت می‌گیرد
    function messageOf(error) {
        return error.blocking !== undefined ? error.message : 'ارتباط با سرور برقرار نشد.';
    }

    return { csrfToken, classifyError, request, messageOf };
})();
