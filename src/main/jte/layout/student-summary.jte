@import com.example.roshdeandishe.web.model.StudentSummary

@param StudentSummary summary
@param String gradesHref

<%-- خلاصهٔ نمره/آزمون/غیبت (changes/student-summary.md): یک تکه‌ی مشترک برای پیشخوانِ دانش‌آموز و کارتِ هر فرزند.
     رقم‌ها با Calendar.digits فارسی می‌شوند؛ میانگین عمداً نشان داده نمی‌شود. --%>
<div class="app-summary">
    <p class="mb-1"><span class="is-size-7 has-text-grey">آخرین نمره‌ها:</span></p>
    @if(summary.hasGrades())
        <ul class="app-summary-list mb-1">
            @for(var grade : summary.grades())
                <li><span>${grade.subject()}<span class="has-text-grey is-size-7"> · ${grade.term()}</span></span>
                    <strong x-text="Calendar.digits('${grade.value()}')">${grade.value()}</strong></li>
            @endfor
        </ul>
        <p class="mb-2 is-size-7"><a href="${gradesHref}">همه‌ی نمره‌ها</a></p>
    @else
        <p class="mb-2 is-size-7">نمره‌ای ثبت نشده است.</p>
    @endif

    <p class="mb-1"><span class="is-size-7 has-text-grey">آزمون‌های پیش‌رو:</span></p>
    @if(summary.hasExams())
        <ul class="app-summary-list mb-2">
            @for(var exam : summary.exams())
                <li><span>${exam.title()}</span><span class="is-size-7 has-text-grey">${exam.when()}</span></li>
            @endfor
        </ul>
    @else
        <p class="mb-2 is-size-7">آزمون نزدیکی ثبت نشده است.</p>
    @endif

    @if(summary.hasAttendance())
        <p class="mb-0">
            <span class="is-size-7 has-text-grey">غیبت امسال:</span>
            <span class="tag is-light summary-absent"><span x-text="Calendar.digits('${summary.attendance().absent()}')">${summary.attendance().absent()}</span>&nbsp;روز غایب</span>
            <span class="tag is-light summary-excused"><span x-text="Calendar.digits('${summary.attendance().excused()}')">${summary.attendance().excused()}</span>&nbsp;موجه</span>
            <span class="tag is-light summary-late"><span x-text="Calendar.digits('${summary.attendance().late()}')">${summary.attendance().late()}</span>&nbsp;دیرآمدن</span>
        </p>
    @endif
</div>
