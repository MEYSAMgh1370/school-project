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
        assignments: [],
        assignStudentIdInput: '',
        submittingAssignment: false,

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

        async toggleAssignments(planId) {
            if (this.expandedPlanId === planId) {
                this.expandedPlanId = null;
                return;
            }
            this.expandedPlanId = planId;
            this.assignStudentIdInput = '';
            try {
                const response = await fetch(`${this.plansBase()}/${planId}/assignments`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.assignments = response.ok ? await response.json() : [];
            } catch (e) {
                this.assignments = [];
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
                await this.toggleAssignments(planId);
                await this.toggleAssignments(planId);
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
                    return;
                }
                await this.loadWeek();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
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
                    return;
                }
                this.notice = 'بازه‌ی مطالعه حذف شد.';
                await this.loadWeek();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        }
    }));
});
