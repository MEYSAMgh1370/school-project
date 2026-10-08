document.addEventListener('alpine:init', () => {
    Alpine.data('examsPage', () => ({
        schoolId: null,
        loading: false,
        error: '',
        notice: '',
        blocked: false,

        exams: [],
        flatTopics: [],
        students: [],
        pupils: [],

        submittingExam: false,
        examForm: { title: '', start: '', end: '', durationMinutes: 60 },

        expandedExamId: null,
        examTab: 'sections',
        sections: [],
        assignedStudentIds: [],
        attemptedStudents: [],
        expandedResultStudentId: null,
        studentResult: null,

        manualSectionForm: { topicId: '', name: '', questionIds: [] },
        questionsForTopic: [],
        submittingManualSection: false,

        autoSectionForm: { topicId: '', name: '', count: 5, lazy: true },
        submittingAutoSection: false,

        assignStudentIdInput: '',
        submittingAssignment: false,

        weeklyForm: { studentId: '', date: '' },
        weeklyResult: null,
        generatingWeekly: false,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;
            this.weeklyForm.date = this.formatLocalDate(new Date());
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.error = '';
                this.notice = '';
                this.blocked = false;
                this.expandedExamId = null;
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

        examsBase() {
            return `/api/admin/schools/${this.schoolId}/exams`;
        },

        topicsBase() {
            return `/api/admin/schools/${this.schoolId}/topics`;
        },

        questionsBase() {
            return `/api/admin/schools/${this.schoolId}/questions`;
        },

        studentsBase() {
            return `/api/admin/schools/${this.schoolId}/students`;
        },

        membershipsBase() {
            return `/api/admin/schools/${this.schoolId}/memberships`;
        },

        flattenTree(nodes, depth) {
            let result = [];
            for (const node of nodes) {
                result.push({ id: node.id, name: node.name, depth });
                result = result.concat(this.flattenTree(node.children, depth + 1));
            }
            return result;
        },

        formatLocalDate(date) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        },

        // آغازِ هفته‌ی مدرسه (شنبه یا دوشنبه، W9) که تاریخ در آن است؛ سرور همین را می‌خواهد
        weekStartOf(dateStr) {
            return this.formatLocalDate(Calendar.weekStartOf(new Date(dateStr + 'T00:00:00'), Calendar.weekStart()));
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

        async classifyError(response) {
            try {
                const body = await response.json();
                const type = body.type || '';
                if (type.endsWith('module-not-entitled')) {
                    return { blocking: true, message: 'این مدرسه ماژول آزمون را نخریده است. برای فعال‌سازی با پشتیبانی تماس بگیرید.' };
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
                const [examsResponse, treeResponse, studentsResponse, membershipsResponse] = await Promise.all([
                    fetch(this.examsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(`${this.topicsBase()}/tree`, { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.studentsBase(), { headers: { 'X-School-ID': this.schoolId } }),
                    fetch(this.membershipsBase(), { headers: { 'X-School-ID': this.schoolId } })
                ]);
                if (!examsResponse.ok) {
                    const classified = await this.classifyError(examsResponse);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.exams = await examsResponse.json();
                this.flatTopics = treeResponse.ok ? this.flattenTree(await treeResponse.json(), 0) : [];
                this.students = studentsResponse.ok ? await studentsResponse.json() : [];
                if (membershipsResponse.ok) {
                    const memberships = await membershipsResponse.json();
                    this.pupils = memberships.filter(m => m.active && m.roleCode === 'STUDENT');
                } else {
                    this.pupils = await window.MemberNames.resolve(
                        this.schoolId,
                        this.students.map(student => student.userId)
                    );
                }
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.loading = false;
            }
        },

        async createExam() {
            this.submittingExam = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(this.examsBase(), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        title: this.examForm.title,
                        start: this.examForm.start,
                        end: this.examForm.end,
                        durationMinutes: parseInt(this.examForm.durationMinutes, 10)
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'آزمون اضافه شد.';
                this.examForm = { title: '', start: '', end: '', durationMinutes: 60 };
                await this.load();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingExam = false;
            }
        },

        // پنلِ مدیریتِ آزمون فقط برای آزمونِ بازشده ساخته می‌شود: پنل‌های بسته (با همه‌ی فهرست‌های تودرتو) در
        // DOM نمی‌آیند. پیش از این همه‌ی پنل‌ها با x-show ساخته می‌شدند و یک صفحه‌ی ۵۰ آزمونی ۶ ثانیه طول می‌کشید.
        expandedExams() {
            return this.exams.filter(exam => exam.id === this.expandedExamId);
        },

        async toggleExam(examId) {
            if (this.expandedExamId === examId) {
                this.expandedExamId = null;
                return;
            }
            this.expandedExamId = examId;
            this.examTab = 'sections';
            this.expandedResultStudentId = null;
            this.studentResult = null;
            await Promise.all([this.loadSections(examId), this.loadAssignedStudents(examId), this.loadAttemptedStudents(examId)]);
        },

        async loadSections(examId) {
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/sections`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.sections = response.ok ? await response.json() : [];
            } catch (e) {
                this.sections = [];
            }
        },

        async onManualTopicChange() {
            this.manualSectionForm.questionIds = [];
            this.questionsForTopic = [];
            if (!this.manualSectionForm.topicId) {
                return;
            }
            try {
                const response = await fetch(`${this.questionsBase()}?topicId=${this.manualSectionForm.topicId}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.questionsForTopic = response.ok ? await response.json() : [];
            } catch (e) {
                this.questionsForTopic = [];
            }
        },

        toggleQuestionSelection(questionId) {
            if (this.manualSectionForm.questionIds.includes(questionId)) {
                this.manualSectionForm.questionIds = this.manualSectionForm.questionIds.filter(id => id !== questionId);
            } else {
                this.manualSectionForm.questionIds = [...this.manualSectionForm.questionIds, questionId];
            }
        },

        async createManualSection(examId) {
            if (this.submittingManualSection) {
                return;
            }
            if (!this.manualSectionForm.topicId) {
                this.notice = '';
                this.error = 'سرفصل بخش را انتخاب کنید.';
                return;
            }
            if (this.manualSectionForm.questionIds.length === 0) {
                this.notice = '';
                this.error = 'دست‌کم یک سؤال را برای بخش انتخاب کنید.';
                return;
            }
            this.submittingManualSection = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/sections`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        topicId: parseInt(this.manualSectionForm.topicId, 10),
                        name: this.manualSectionForm.name || null,
                        questionIds: this.manualSectionForm.questionIds
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'بخش اضافه شد.';
                this.manualSectionForm = { topicId: '', name: '', questionIds: [] };
                this.questionsForTopic = [];
                await this.loadSections(examId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingManualSection = false;
            }
        },

        async createAutoSection(examId) {
            this.submittingAutoSection = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/sections/auto`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({
                        topicId: parseInt(this.autoSectionForm.topicId, 10),
                        name: this.autoSectionForm.name || null,
                        count: parseInt(this.autoSectionForm.count, 10),
                        lazy: this.autoSectionForm.lazy
                    })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'بخش با انتخاب خودکار سؤال اضافه شد.';
                this.autoSectionForm = { topicId: '', name: '', count: 5, lazy: true };
                await this.loadSections(examId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingAutoSection = false;
            }
        },

        async loadAssignedStudents(examId) {
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/students`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.assignedStudentIds = response.ok ? await response.json() : [];
            } catch (e) {
                this.assignedStudentIds = [];
            }
        },

        async assignStudent(examId) {
            if (this.submittingAssignment) {
                return;
            }
            if (!this.assignStudentIdInput) {
                this.notice = '';
                this.error = 'یک دانش‌آموز را برای تخصیص انتخاب کنید.';
                return;
            }
            this.submittingAssignment = true;
            this.error = '';
            this.notice = '';
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/assign`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-School-ID': this.schoolId,
                        'X-XSRF-TOKEN': this.csrfToken()
                    },
                    body: JSON.stringify({ studentIds: [parseInt(this.assignStudentIdInput, 10)] })
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.notice = 'دانش‌آموز به آزمون تخصیص داده شد.';
                this.assignStudentIdInput = '';
                await this.loadAssignedStudents(examId);
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.submittingAssignment = false;
            }
        },

        async loadAttemptedStudents(examId) {
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/results`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                this.attemptedStudents = response.ok ? await response.json() : [];
            } catch (e) {
                this.attemptedStudents = [];
            }
        },

        async toggleResult(examId, studentId) {
            if (this.expandedResultStudentId === studentId) {
                this.expandedResultStudentId = null;
                this.studentResult = null;
                return;
            }
            this.expandedResultStudentId = studentId;
            this.studentResult = null;
            this.error = '';
            try {
                const response = await fetch(`${this.examsBase()}/${examId}/results/${studentId}`, {
                    headers: { 'X-School-ID': this.schoolId }
                });
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    return;
                }
                this.studentResult = await response.json();
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            }
        },

        async generateWeekly() {
            if (this.generatingWeekly) {
                return;
            }
            if (!this.weeklyForm.studentId) {
                this.notice = '';
                this.error = 'یک دانش‌آموز را برای ساخت آزمون هفتگی انتخاب کنید.';
                return;
            }
            this.generatingWeekly = true;
            this.error = '';
            this.notice = '';
            this.weeklyResult = null;
            try {
                const weekStart = this.weekStartOf(this.weeklyForm.date);
                const response = await fetch(
                    `${this.examsBase()}/weekly?studentId=${this.weeklyForm.studentId}&weekStart=${weekStart}`,
                    {
                        method: 'POST',
                        headers: { 'X-School-ID': this.schoolId, 'X-XSRF-TOKEN': this.csrfToken() }
                    }
                );
                if (!response.ok) {
                    const classified = await this.classifyError(response);
                    this.error = classified.message;
                    this.blocked = classified.blocking;
                    return;
                }
                this.weeklyResult = await response.json();
                this.notice = 'آزمون هفتگی ساخته شد.';
            } catch (e) {
                this.error = 'ارتباط با سرور برقرار نشد.';
            } finally {
                this.generatingWeekly = false;
            }
        }
    }));
});
