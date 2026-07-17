// «برنامه‌ی مطالعه‌ی من» (دانش‌آموز) - گرید هفتگی از StudyInterval های
// خودِ کاربر + یک تب آمار، هر دو کاملاً از یک fetch هفتگی محاسبه می‌شوند
// (هیچ endpoint جداگانه‌ای برای آمار لازم نیست). داده از
// studyplan.MyStudyIntervalController می‌آید (الگوی my-exams.js: بدون
// X-School-ID، schoolId در path).
document.addEventListener('alpine:init', () => {
    Alpine.data('myStudyPlanPage', () => ({
        schoolId: null,
        tab: 'grid',
        loading: false,
        error: '',
        notice: '',

        weekStart: null, // Date محلی، نیمه‌شبِ شنبه‌ی هفته‌ی انتخاب‌شده
        intervals: [],

        pickerOpen: false,
        pickerMonth: { jy: 1404, jm: 1 },

        dialog: null,
        dialogDone: false,
        dialogTestCount: 0,
        saving: false,

        hourStart: 6,
        hourEnd: 24,
        hourPx: 60,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.weekStart = this.mostRecentSaturday(new Date());
            this.syncPickerMonth();
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        // ----------------------------------------------------------- تاریخ

        // toISOString() به UTC تبدیل می‌کند و نزدیک نیمه‌شب در timezoneهای
        // غیر UTC یک روز جابه‌جا می‌شود - همیشه از مقادیر محلی رشته می‌سازیم
        // (همان قرارداد study-plans.js).
        formatLocalDate(date) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        },

        // Jalali.toGregorian یک Date در نیمه‌شبِ UTC برمی‌گرداند (jalali.js:
        // «تبدیل تاریخ جلالی به Date میلادی UTC»)؛ برای اینکه بتوان با
        // متدهای محلی (getDate/setDate/...) رویش حساب کرد، باید اول به
        // نیمه‌شبِ محلیِ همان روز تبدیل شود - وگرنه در timezoneهای منفی
        // (غرب UTC) یک روز عقب می‌افتد.
        toLocalMidnight(utcDate) {
            return new Date(utcDate.getUTCFullYear(), utcDate.getUTCMonth(), utcDate.getUTCDate());
        },

        mostRecentSaturday(date) {
            const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
            const daysSinceSaturday = (d.getDay() + 1) % 7; // یکشنبه=0 در getDay -> شنبه باید ۰ شود
            d.setDate(d.getDate() - daysSinceSaturday);
            return d;
        },

        faDigits(value) {
            return Jalali.toPersianDigits(value);
        },

        timeLabel(date) {
            return this.faDigits(date.getHours()) + ':' + this.faDigits(String(date.getMinutes()).padStart(2, '0'));
        },

        timeRangeLabel(start, end) {
            return this.timeLabel(start) + ' – ' + this.timeLabel(end);
        },

        formatDuration(hours) {
            const h = Math.floor(hours);
            const m = Math.round((hours - h) * 60);
            if (h === 0) return this.faDigits(m) + ' دقیقه';
            if (m === 0) return this.faDigits(h) + ' ساعت';
            return this.faDigits(h) + ' ساعت و ' + this.faDigits(m) + ' دقیقه';
        },

        // ------------------------------------------------------------ fetch

        intervalsBase() {
            return `/api/me/schools/${this.schoolId}/study-intervals`;
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        async parseError(response) {
            try {
                const body = await response.json();
                return body.detail || body.title || 'خطایی رخ داد.';
            } catch (e) {
                return 'خطایی رخ داد. (' + response.status + ')';
            }
        },

        async load() {
            if (!this.schoolId) {
                return;
            }
            this.loading = true;
            this.error = '';
            try {
                const response = await fetch(`${this.intervalsBase()}?date=${this.formatLocalDate(this.weekStart)}`);
                if (!response.ok) {
                    this.error = await this.parseError(response);
                    this.intervals = [];
                    return;
                }
                this.intervals = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        // --------------------------------------------------------- هفته‌ها

        get isCurrentWeek() {
            return this.formatLocalDate(this.weekStart) === this.formatLocalDate(this.mostRecentSaturday(new Date()));
        },

        goPrevWeek() {
            const d = new Date(this.weekStart);
            d.setDate(d.getDate() - 7);
            this.weekStart = d;
            this.syncPickerMonth();
            this.load();
        },

        goNextWeek() {
            const d = new Date(this.weekStart);
            d.setDate(d.getDate() + 7);
            this.weekStart = d;
            this.syncPickerMonth();
            this.load();
        },

        goThisWeek() {
            this.weekStart = this.mostRecentSaturday(new Date());
            this.syncPickerMonth();
            this.load();
        },

        syncPickerMonth() {
            this.pickerMonth = Jalali.toJalali(this.weekStart.getFullYear(), this.weekStart.getMonth() + 1, this.weekStart.getDate());
        },

        get weekRangeLabel() {
            const start = Jalali.toJalali(this.weekStart.getFullYear(), this.weekStart.getMonth() + 1, this.weekStart.getDate());
            const endDate = new Date(this.weekStart);
            endDate.setDate(endDate.getDate() + 6);
            const end = Jalali.toJalali(endDate.getFullYear(), endDate.getMonth() + 1, endDate.getDate());
            const startLabel = this.faDigits(start.jd) + ' ' + Jalali.monthNames[start.jm - 1];
            if (start.jy === end.jy && start.jm === end.jm) {
                return this.faDigits(start.jd) + ' تا ' + this.faDigits(end.jd) + ' ' + Jalali.monthNames[start.jm - 1] + ' ' + this.faDigits(start.jy);
            }
            if (start.jy === end.jy) {
                return startLabel + ' تا ' + this.faDigits(end.jd) + ' ' + Jalali.monthNames[end.jm - 1] + ' ' + this.faDigits(start.jy);
            }
            return startLabel + ' ' + this.faDigits(start.jy) + ' تا ' + this.faDigits(end.jd) + ' ' + Jalali.monthNames[end.jm - 1] + ' ' + this.faDigits(end.jy);
        },

        get weekDays() {
            const todayIso = this.formatLocalDate(new Date());
            const days = [];
            for (let i = 0; i < 7; i++) {
                const d = new Date(this.weekStart);
                d.setDate(d.getDate() + i);
                const iso = this.formatLocalDate(d);
                const jal = Jalali.toJalali(d.getFullYear(), d.getMonth() + 1, d.getDate());
                days.push({
                    iso,
                    name: Jalali.weekdayNames[i],
                    dateNum: this.faDigits(jal.jd),
                    isToday: iso === todayIso,
                    blocks: this.blocksForDay(iso)
                });
            }
            return days;
        },

        get totalIntervals() {
            return this.intervals.length;
        },

        blocksForDay(iso) {
            return this.intervals
                .filter(iv => iv.start.slice(0, 10) === iso)
                .map(iv => this.toBlock(iv))
                .sort((a, b) => a.top - b.top);
        },

        toBlock(iv) {
            const start = new Date(iv.start);
            const end = new Date(iv.end);
            const startHour = start.getHours() + start.getMinutes() / 60;
            const durationHours = (end - start) / 3600000;
            const top = Math.max(0, Math.round((startHour - this.hourStart) * this.hourPx));
            const height = Math.max(16, Math.round(durationHours * this.hourPx - 2));
            return {
                id: iv.id,
                topicId: iv.topicId,
                topicName: iv.topicName,
                done: iv.done,
                testCount: iv.testCount,
                durationHours,
                top,
                height,
                showTime: durationHours > 1,
                timeLabel: this.timeRangeLabel(start, end)
            };
        },

        get hourMarks() {
            const marks = [];
            for (let h = this.hourStart; h <= this.hourEnd; h += 1) {
                marks.push(h);
            }
            return marks;
        },

        hourMarkTop(h) {
            return (h - this.hourStart) * this.hourPx;
        },

        hourMarkLabel(h) {
            return this.faDigits(h) + ':۰۰';
        },

        // ------------------------------------------------------- ماه‌نما

        togglePicker() {
            this.pickerOpen = !this.pickerOpen;
        },

        pickerPrevMonth() {
            let { jy, jm } = this.pickerMonth;
            jm -= 1;
            if (jm < 1) {
                jm = 12;
                jy -= 1;
            }
            this.pickerMonth = { jy, jm };
        },

        pickerNextMonth() {
            let { jy, jm } = this.pickerMonth;
            jm += 1;
            if (jm > 12) {
                jm = 1;
                jy += 1;
            }
            this.pickerMonth = { jy, jm };
        },

        get pickerMonthLabel() {
            return Jalali.monthNames[this.pickerMonth.jm - 1] + ' ' + this.faDigits(this.pickerMonth.jy);
        },

        daysInJalaliMonth(jy, jm) {
            const start = Jalali.toGregorian(jy, jm, 1);
            const nextJy = jm === 12 ? jy + 1 : jy;
            const nextJm = jm === 12 ? 1 : jm + 1;
            const end = Jalali.toGregorian(nextJy, nextJm, 1);
            return Math.round((end - start) / 86400000);
        },

        get pickerCells() {
            const { jy, jm } = this.pickerMonth;
            const firstLocal = this.toLocalMidnight(Jalali.toGregorian(jy, jm, 1));
            const firstCol = Jalali.weekdayIndex(firstLocal);
            const daysCount = this.daysInJalaliMonth(jy, jm);
            const weekStartIso = this.formatLocalDate(this.weekStart);
            const weekEndDate = new Date(this.weekStart);
            weekEndDate.setDate(weekEndDate.getDate() + 6);
            const weekEndIso = this.formatLocalDate(weekEndDate);
            const todayIso = this.formatLocalDate(new Date());

            const cells = [];
            for (let i = 0; i < firstCol; i += 1) {
                cells.push({ key: 'b' + i, blank: true });
            }
            for (let day = 1; day <= daysCount; day += 1) {
                const localDate = this.toLocalMidnight(Jalali.toGregorian(jy, jm, day));
                const iso = this.formatLocalDate(localDate);
                cells.push({
                    key: 'd' + day,
                    blank: false,
                    label: this.faDigits(day),
                    inWeek: iso >= weekStartIso && iso <= weekEndIso,
                    isToday: iso === todayIso,
                    date: localDate
                });
            }
            return cells;
        },

        pickDay(cell) {
            this.weekStart = this.mostRecentSaturday(cell.date);
            this.pickerOpen = false;
            this.load();
        },

        // -------------------------------------------------------- گفتگو

        openLog(block) {
            this.dialog = block;
            this.dialogDone = block.done;
            this.dialogTestCount = block.testCount;
            this.notice = '';
            this.error = '';
        },

        closeDialog() {
            this.dialog = null;
        },

        async saveDialog() {
            if (!this.dialog) {
                return;
            }
            this.saving = true;
            this.error = '';
            try {
                const response = await fetch(`${this.intervalsBase()}/${this.dialog.id}`, {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ done: this.dialogDone, testCount: this.dialogTestCount })
                });
                if (!response.ok) {
                    this.error = await this.parseError(response);
                    return;
                }
                this.notice = 'مطالعه ثبت شد.';
                this.dialog = null;
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.saving = false;
            }
        },

        // ------------------------------------------------------------ آمار

        get doneIntervals() {
            return this.intervals.filter(iv => iv.done);
        },

        get totalHours() {
            return this.intervals.reduce((sum, iv) => sum + (new Date(iv.end) - new Date(iv.start)) / 3600000, 0);
        },

        get completionPct() {
            return this.intervals.length ? Math.round(this.doneIntervals.length / this.intervals.length * 100) : 0;
        },

        get totalTestCount() {
            return this.intervals.reduce((sum, iv) => sum + (iv.testCount || 0), 0);
        },

        get kpis() {
            return [
                { label: 'مجموع ساعات برنامه‌ریزی‌شده', value: this.formatDuration(this.totalHours) },
                { label: 'نرخ انجام جلسات', value: this.faDigits(this.completionPct) + '٪' },
                { label: 'جلسات کامل‌شده', value: this.faDigits(this.doneIntervals.length) + ' از ' + this.faDigits(this.intervals.length) },
                { label: 'تعداد تست‌های ثبت‌شده', value: this.faDigits(this.totalTestCount) }
            ];
        },

        get dayBars() {
            const days = this.weekDays;
            const totals = days.map(day => day.blocks.reduce((sum, b) => sum + b.durationHours, 0));
            const doneTotals = days.map(day => day.blocks.filter(b => b.done).reduce((sum, b) => sum + b.durationHours, 0));
            const maxHours = Math.max(1, ...totals);
            return days.map((day, i) => ({
                name: day.name,
                valueLabel: this.faDigits(Math.round(totals[i] * 10) / 10),
                trackHeight: Math.max(2, Math.round(totals[i] / maxHours * 130)),
                fillPct: totals[i] > 0 ? Math.round(doneTotals[i] / totals[i] * 100) : 0
            }));
        },

        get topicBars() {
            const map = new Map();
            for (const iv of this.intervals) {
                const hours = (new Date(iv.end) - new Date(iv.start)) / 3600000;
                const entry = map.get(iv.topicId) || { topicName: iv.topicName, hours: 0, doneCount: 0, totalCount: 0 };
                entry.hours += hours;
                entry.totalCount += 1;
                if (iv.done) {
                    entry.doneCount += 1;
                }
                map.set(iv.topicId, entry);
            }
            const list = Array.from(map.values()).sort((a, b) => b.hours - a.hours);
            const maxHours = Math.max(0.1, ...list.map(e => e.hours));
            return list.map(e => ({
                topicName: e.topicName,
                valueLabel: this.faDigits(e.doneCount) + ' از ' + this.faDigits(e.totalCount) + ' جلسه',
                fillPct: Math.round(e.hours / maxHours * 100)
            }));
        }
    }));
});
