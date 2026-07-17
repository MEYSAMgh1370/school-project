document.addEventListener('alpine:init', () => {
    Alpine.data('studyPlansPage', () => ({
        schoolId: null,
        loading: false,
        error: '',
        notice: '',
        blocked: false,

        plans: [],
        flatTopics: [],
        students: [],
        pupils: [],

        submittingPlan: false,
        planForm: { title: '', weights: [{ topicId: '', weight: 1, priority: 1 }] },

        expandedPlanId: null,
        planDetailTab: 'assignments',
        assignments: [],
        assignStudentIdInput: '',
        submittingAssignment: false,

        weekCourses: [],
        loadingWeekCourses: false,
        weekCourseForm: { weekStart: '', courseTopicId: '', subTopicIds: [] },
        submittingWeekCourse: false,

        copyPanelOpen: false,
        copyTargetIds: [],
        copyingWeek: false,
        copyResults: [],

        selectedStudentId: '',
        weekdays: [
            { code: 'SAT', label: 'شنبه' },
            { code: 'SUN', label: 'یکشنبه' },
            { code: 'MON', label: 'دوشنبه' },
            { code: 'TUE', label: 'سه‌شنبه' },
            { code: 'WED', label: 'چهارشنبه' },
            { code: 'THU', label: 'پنجشنبه' },
            { code: 'FRI', label: 'جمعه' }
        ],
        scheduleForm: {},
        currentActive: false,
        savingSchedule: false,

        weekDate: '',
        intervals: [],
        loadingWeek: false,
        suggesting: false,
        clearing: false,
        submittingInterval: false,
        intervalForm: { topicId: '', start: '', end: '' },

        // گرید هفتگی (my-study-plan.css) - hourStart/hourEnd/hourPx باید با
        // sp-day-body/sp-axis آن فایل هماهنگ بمانند (ببینید کامنت بالای فایل).
        hourStart: 6,
        hourEnd: 24,
        hourPx: 60,
        gridBlock: null,
        gridBlockStep: 'detail',

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.resetScheduleForm();
            this.weekDate = this.formatLocalDate(new Date());
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.selectedStudentId = '';
                this.expandedPlanId = null;
                this.copyPanelOpen = false;
                this.copyTargetIds = [];
                this.copyResults = [];
                this.load();
            });
            if (this.schoolId) {
                this.load();
            }
        },

        csrfToken() {
            const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return match ? decodeURIComponent(match[1]) : '';
        },

        plansBase() {
            return `/api/admin/schools/${this.schoolId}/study-plans`;
        },

        topicsBase() {
            return `/api/admin/schools/${this.schoolId}/topics`;
        },

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        scheduleBase(studentId) {
            return `/api/admin/schools/${this.schoolId}/students/${studentId}/schedule`;
        },

        intervalsBase(studentId) {
            return `/api/admin/schools/${this.schoolId}/students/${studentId}/study-intervals`;
        },

        flattenTree(nodes, depth) {
            let result = [];
            for (const node of nodes) {
                result.push({ id: node.id, name: node.name, depth });
                result = result.concat(this.flattenTree(node.children, depth + 1));
            }
            return result;
        },

        resetScheduleForm() {
            const form = {};
            for (const day of this.weekdays) {
                form[day.code] = [];
            }
            this.scheduleForm = form;
        },

        topicName(topicId) {
            const topic = this.flatTopics.find(t => t.id === topicId);
            return topic ? topic.name : `سرفصل #${topicId}`;
        },

        subTopicNames(subTopicIds) {
            return (subTopicIds || []).map(id => this.topicName(id)).join('، ');
        },

        otherStudents() {
            return this.students.filter(s => String(s.studentId) !== String(this.selectedStudentId));
        },

        studentLabel(studentId) {
            const student = this.students.find(s => s.studentId === studentId);
            if (!student) {
                return `دانش‌آموز #${studentId}`;
            }
            const pupil = this.pupils.find(p => p.userId === student.userId);
            return pupil ? pupil.fullName : `دانش‌آموز #${studentId}`;
        },

        // toISOString() به UTC تبدیل می‌کند و نزدیک نیمه‌شب در timezoneهای
        // غیر UTC یک روز جابه‌جا می‌شود - همیشه از مقادیر محلی رشته می‌سازیم.
        formatLocalDate(date) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        },

        // شنبه‌ی همان هفته یا پیش از تاریخ داده‌شده - کاربر هر روزی از هفته را
        // انتخاب می‌کند، این تابع محاسبه‌ی «weekStart باید شنبه باشد» را از او
        // پنهان می‌کند (پیشنهاد خودکار/خالی‌کردن هفته هر دو weekStart دقیق
        // می‌خواهند).
        mostRecentSaturday(dateStr) {
            const date = new Date(dateStr + 'T00:00:00');
            const daysSinceSaturday = (date.getDay() - 6 + 7) % 7;
            date.setDate(date.getDate() - daysSinceSaturday);
            return this.formatLocalDate(date);
        },

        addWeightRow() {
            this.planForm.weights.push({ topicId: '', weight: 1, priority: this.planForm.weights.length + 1 });
        },

        removeWeightRow(index) {
            this.planForm.weights.splice(index, 1);
        },

        addTimeRange(day) {
            this.scheduleForm[day].push({ start: '', end: '' });
        },

        removeTimeRange(day, index) {
            this.scheduleForm[day].splice(index, 1);
        },

        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول برنامه‌ی مطالعه را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
                }
                if (type.endsWith('permission-denied')) {
                    return { blocking: true, message: 'نقش انتخاب‌شده‌ی شما اجازه‌ی دسترسی به این بخش را ندارد.' };
                }
                return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
            } catch (e) {
                return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
            }
        },

        async load() {
            this.loading = true;
            this.error = '';
            this.blocked = false;
            try {
                const [plansResponse, treeResponse, studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.plansBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(`${this.topicsBase()}/tree`, { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!plansResponse.ok) {
                    const classified = await this.classifyError(plansResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.plans = await plansResponse.json();
                this.flatTopics = treeResponse.ok ? this.flattenTree(await treeResponse.json(), 0) : [];
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
                const memberships = membershipsResponse.ok ? await membershipsResponse.json() : [];
                this.pupils = memberships.filter(m => m.active && m.roleCode === 'STUDENT');
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async createPlan() {
            this.submittingPlan = true;
            this.error = '';
            this.notice = '';
            try {
                const weights = this.planForm.weights
                    .filter(w => w.topicId)
                    .map(w => ({
                        topicId: parseInt(w.topicId, 10),
                        weight: parseFloat(w.weight),
                        priority: parseInt(w.priority, 10)
                    }));
                const response = await fetch(this.plansBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ title: this.planForm.title, weights })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'برنامه اضافه شد.';
                this.planForm = { title: '', weights: [{ topicId: '', weight: 1, priority: 1 }] };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingPlan = false;
            }
        },

        async togglePlanDetails(planId) {
            if (this.expandedPlanId === planId) {
                this.expandedPlanId = null;
                return;
            }
            this.expandedPlanId = planId;
            this.planDetailTab = 'assignments';
            this.assignStudentIdInput = '';
            await this.loadAssignments(planId);
        },

        async loadAssignments(planId) {
            try {
                const response = await fetch(`${this.plansBase()}/${planId}/assignments`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.assignments = response.ok ? await response.json() : [];
            } catch (e) {
                this.assignments = [];
            }
        },

        // سرفصل‌های هفتگیِ یک برنامه (WeekCourse) - lazy-load می‌شود، فقط وقتی
        // این تب برای اولین بار برای این برنامه باز شود؛ همان الگوی lazy که
        // toggleGuardianPanel در students.js برای بارگذاری ولی‌ها استفاده می‌کند.
        async selectWeekCoursesTab(planId) {
            this.planDetailTab = 'weekCourses';
            this.weekCourseForm = { weekStart: '', courseTopicId: '', subTopicIds: [] };
            await this.loadWeekCourses(planId);
        },

        async loadWeekCourses(planId) {
            this.loadingWeekCourses = true;
            try {
                const response = await fetch(`${this.plansBase()}/${planId}/week-courses`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.weekCourses = response.ok ? await response.json() : [];
            } catch (e) {
                this.weekCourses = [];
            } finally {
                this.loadingWeekCourses = false;
            }
        },

        toggleWeekCourseSubTopic(topicId) {
            const index = this.weekCourseForm.subTopicIds.indexOf(topicId);
            if (index >= 0) {
                this.weekCourseForm.subTopicIds.splice(index, 1);
                return;
            }
            // سقف ۱۰ زیرسرفصل - همان محدودیتی که WeekCourseTopicEntity سمت
            // سرور اعمال می‌کند (پورت ArrayField(size=10) موshavere)
            if (this.weekCourseForm.subTopicIds.length >= 10) {
                return;
            }
            this.weekCourseForm.subTopicIds.push(topicId);
        },

        async createWeekCourse(planId) {
            this.submittingWeekCourse = true;
            this.error = '';
            this.notice = '';
            try {
                const weekStart = this.mostRecentSaturday(this.weekCourseForm.weekStart);
                const response = await fetch(`${this.plansBase()}/${planId}/week-courses`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        weekStart,
                        courseTopicId: parseInt(this.weekCourseForm.courseTopicId, 10),
                        subTopicIds: this.weekCourseForm.subTopicIds
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'سرفصل هفتگی اضافه شد.';
                this.weekCourseForm = { weekStart: '', courseTopicId: '', subTopicIds: [] };
                await this.loadWeekCourses(planId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingWeekCourse = false;
            }
        },

        toggleCopyTarget(studentId) {
            const index = this.copyTargetIds.indexOf(studentId);
            if (index >= 0) {
                this.copyTargetIds.splice(index, 1);
            } else {
                this.copyTargetIds.push(studentId);
            }
        },

        async copyWeekToOthers() {
            this.copyingWeek = true;
            this.error = '';
            this.notice = '';
            this.copyResults = [];
            try {
                const weekStart = this.mostRecentSaturday(this.weekDate);
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}/copy`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ weekStart, targetStudentIds: this.copyTargetIds })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.copyResults = await response.json();
                const successCount = this.copyResults.filter(r => r.ok).length;
                this.notice = `کپی هفته انجام شد: ${successCount} از ${this.copyResults.length} مورد با موفقیت.`;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.copyingWeek = false;
            }
        },

        async assignStudent(planId) {
            this.submittingAssignment = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.plansBase()}/${planId}/assignments`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ studentId: parseInt(this.assignStudentIdInput, 10) })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'برنامه به دانش‌آموز تخصیص داده شد.';
                this.assignStudentIdInput = '';
                await this.loadAssignments(planId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingAssignment = false;
            }
        },

        async selectStudent(studentId) {
            this.selectedStudentId = studentId;
            this.error = '';
            this.notice = '';
            this.copyPanelOpen = false;
            this.copyTargetIds = [];
            this.copyResults = [];
            await Promise.all([this.loadSchedule(), this.loadWeek()]);
        },

        async loadSchedule() {
            if (!this.selectedStudentId) {
                return;
            }
            try {
                const response = await fetch(this.scheduleBase(this.selectedStudentId), {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                const info = await response.json();
                this.resetScheduleForm();
                for (const day of this.weekdays) {
                    this.scheduleForm[day.code] = (info.defaultSchedule && info.defaultSchedule[day.code]) || [];
                }
                this.currentActive = info.currentActive;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        async saveDefaultSchedule() {
            this.savingSchedule = true;
            this.error = '';
            this.notice = '';
            try {
                const payload = {};
                for (const day of this.weekdays) {
                    payload[day.code] = this.scheduleForm[day.code].filter(r => r.start && r.end);
                }
                const response = await fetch(`${this.scheduleBase(this.selectedStudentId)}/default`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify(payload)
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'برنامه‌ی زمانی هفتگی ذخیره شد.';
                await this.loadSchedule();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.savingSchedule = false;
            }
        },

        async loadWeek() {
            if (!this.selectedStudentId) {
                return;
            }
            this.loadingWeek = true;
            try {
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}?date=${this.weekDate}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    this.intervals = [];
                    return;
                }
                this.intervals = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loadingWeek = false;
            }
        },

        // -------------------------------------------------------- گرید هفتگی

        get weekDays() {
            const start = new Date(this.mostRecentSaturday(this.weekDate) + 'T00:00:00');
            const todayIso = this.formatLocalDate(new Date());
            const days = [];
            for (let i = 0; i < 7; i++) {
                const d = new Date(start);
                d.setDate(d.getDate() + i);
                const iso = this.formatLocalDate(d);
                days.push({
                    iso,
                    name: this.weekdays[i].label,
                    dateNum: d.getDate(),
                    isToday: iso === todayIso,
                    blocks: this.blocksForDay(iso)
                });
            }
            return days;
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
            const pad2 = n => String(n).padStart(2, '0');
            return {
                id: iv.id,
                interval: iv,
                topicName: this.topicName(iv.topicId),
                done: iv.done,
                top,
                height,
                showTime: durationHours > 1,
                timeLabel: `${pad2(start.getHours())}:${pad2(start.getMinutes())} – ${pad2(end.getHours())}:${pad2(end.getMinutes())}`
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
            return String(h).padStart(2, '0') + ':00';
        },

        openGridBlock(block) {
            this.gridBlock = block;
            this.gridBlockStep = 'detail';
        },

        closeGridBlock() {
            this.gridBlock = null;
        },

        async toggleGridBlockDone() {
            const ok = await this.toggleIntervalDone(this.gridBlock.interval);
            if (ok) {
                this.closeGridBlock();
            }
        },

        async deleteGridBlock() {
            const ok = await this.deleteInterval(this.gridBlock.interval);
            if (ok) {
                this.closeGridBlock();
            }
        },

        async suggestWeek() {
            this.suggesting = true;
            this.error = '';
            this.notice = '';
            try {
                const weekStart = this.mostRecentSaturday(this.weekDate);
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}/suggest?weekStart=${weekStart}`, {
                    method: 'POST',
                    headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'جدول مطالعه‌ی این هفته پیشنهاد شد.';
                await this.loadWeek();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.suggesting = false;
            }
        },

        async clearWeek() {
            this.clearing = true;
            this.error = '';
            this.notice = '';
            try {
                const weekStart = this.mostRecentSaturday(this.weekDate);
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}/clear?weekStart=${weekStart}`, {
                    method: 'POST',
                    headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'بازه‌های این هفته پاک شد.';
                await this.loadWeek();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.clearing = false;
            }
        },

        async createInterval() {
            this.submittingInterval = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.intervalsBase(this.selectedStudentId), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        topicId: parseInt(this.intervalForm.topicId, 10),
                        subTopicIds: [],
                        start: this.intervalForm.start,
                        end: this.intervalForm.end
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'بازه‌ی مطالعه اضافه شد.';
                this.intervalForm = { topicId: '', start: '', end: '' };
                await this.loadWeek();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingInterval = false;
            }
        },

        async toggleIntervalDone(interval) {
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}/${interval.id}`, {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ done: !interval.done, testCount: null })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return false;
                }
                this.notice = 'وضعیت بازه به‌روزرسانی شد.';
                await this.loadWeek();
                return true;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
                return false;
            }
        },

        async deleteInterval(interval) {
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.intervalsBase(this.selectedStudentId)}/${interval.id}`, {
                    method: 'DELETE',
                    headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return false;
                }
                this.notice = 'بازه‌ی مطالعه حذف شد.';
                await this.loadWeek();
                return true;
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
                return false;
            }
        }
    }));
});
