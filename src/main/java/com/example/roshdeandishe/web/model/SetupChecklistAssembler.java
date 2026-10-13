package com.example.roshdeandishe.web.model;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.iam.IamFacade;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * چک‌لیستِ «راه‌اندازی مدرسه»، فقط از رابطِ عمومیِ {@link IamFacade} و {@link AcademicFacade}. همه‌ی گام‌ها
 * در همین یک متد ساخته می‌شوند، تا افزودنِ گامِ تازه (مثلاً مشخصاتِ کاملِ مدرسه) یا تبدیلِ بعدیِ آن به ویزارد
 * فقط افزودنِ یک ورودی باشد و به template دست نخورد.
 * <p>
 * ترتیب از وابستگیِ داده می‌آید: کلاس معلمِ اختیاری دارد ولی دانش‌آموز بدونِ کلاس ثبت نمی‌شود، و معلم
 * طبیعتاً پیش از کلاس دعوت می‌شود. «پخشِ نشانیِ مدرسه» گام نیست چون از روی داده قابلِ تشخیص نیست؛ باکسِ
 * نشانی همان‌طور که بود روی پیشخوان می‌ماند.
 */
@Component
public class SetupChecklistAssembler {

    private static final String TEACHER_ROLE = "TEACHER";

    private final IamFacade iamFacade;
    private final AcademicFacade academicFacade;

    SetupChecklistAssembler(IamFacade iamFacade, AcademicFacade academicFacade) {
        this.iamFacade = iamFacade;
        this.academicFacade = academicFacade;
    }

    public SetupChecklistRow forAdminSchool(Long schoolId, String schoolName) {
        boolean hasTeacher = iamFacade.findMembershipsBySchool(schoolId).stream()
                .anyMatch(m -> TEACHER_ROLE.equals(m.roleCode()));
        boolean hasClass = !academicFacade.listClasses(schoolId).isEmpty();
        boolean hasStudent = !academicFacade.listStudents(schoolId).isEmpty();
        return new SetupChecklistRow(schoolId, schoolName, List.of(
                new SetupStep("teacher", "دعوت معلم",
                        "معلم‌ها را با ایمیل دعوت کنید تا حساب خود را فعال کنند.", "/people", hasTeacher),
                new SetupStep("class", "ساخت کلاس",
                        "کلاس‌های مدرسه را بسازید؛ معلم هر کلاس را می‌توانید همان‌جا انتخاب کنید.",
                        "/classes", hasClass),
                new SetupStep("student", "افزودن دانش‌آموز",
                        "دانش‌آموزان را در یک کلاس ثبت‌نام کنید.", "/students", hasStudent)));
    }
}
