package com.example.roshdeandishe.web.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * منبع واحدِ لینک‌های صفحه‌های مدیریتی: هم سایدبارِ {@code layout/navbar.jte}
 * و هم کارت‌های «دسترسی سریع» داشبورد از همین فهرست رندر می‌شوند، تا لینک
 * جدید فقط یک‌جا اضافه شود و دو فهرست ناهماهنگ نشوند (UX_ROADMAP.md §2:
 * هیچ URL یتیمی وجود ندارد - هر صفحه‌ی مدیریتیِ جدید همین‌جا ثبت می‌شود).
 * <p>
 * نقش‌ها فقط تعیین‌کننده‌ی <b>نمایش</b> هستند (نقشِ انتخاب‌شده در سوییچر،
 * client-side)؛ کنترل دسترسی همیشه سمت سرور می‌ماند و همه‌ی نقش‌های فعال را
 * چک می‌کند (CLAUDE.md بند ۱۱ سؤال ۲). کدهای نقش مثل {@link RoleLabels}
 * literal هستند. داده ثابت است و per-request نیست، پس template آن را مستقیم
 * صدا می‌زند و هیچ page controllerی لازم نیست چیزی به مدل اضافه کند.
 */
public final class NavCatalog {

    private static final String ADMIN = "SCHOOL_ADMIN";
    private static final String TEACHER = "TEACHER";
    private static final String STUDENT = "STUDENT";
    private static final String PARENT = "PARENT";
    private static final String FINANCE = "FINANCE_ADMIN";

    private static final List<Group> GROUPS = List.of(
            new Group("school", "مدرسه", List.of(
                    new Link("/people", "اعضای مدرسه", "افزودن و مدیریت اعضای مدرسه",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("MEMBERSHIP_MANAGE"),
                    new Link("/students", "دانش‌آموزان", "فهرست دانش‌آموزان مدرسه",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("STUDENT_ENROLL"),
                    new Link("/roles", "نقش‌ها و دسترسی‌ها", "تعریف نقش‌ها و دسترسی‌هایشان",
                            List.of(ADMIN), List.of()).withPermissions("ROLE_MANAGE"),
                    new Link("/academic-year", "سال تحصیلی", "انتخاب سال تحصیلی جاریِ مدرسه",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("ACADEMIC_YEAR_MANAGE"))),
            new Group("education", "آموزش", List.of(
                    new Link("/classes", "کلاس‌ها", "کلاس‌ها و ثبت‌نام دانش‌آموزان",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("CLASS_VIEW"),
                    new Link("/timetable", "برنامه‌ی کلاسی", "زنگ‌ها و برنامه‌ی هفتگیِ کلاس‌ها",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("TIMETABLE_MANAGE"),
                    new Link("/my-timetable", "برنامه‌ی کلاسی من", "برنامه‌ی هفتگیِ شما",
                            List.of(STUDENT, PARENT, TEACHER), List.of(STUDENT, PARENT, TEACHER)),
                    new Link("/curriculum", "سرفصل‌ها و بانک سؤال", "سرفصل‌های درسی و بانک سؤال",
                            List.of(ADMIN, TEACHER), List.of()).withPermissions("CURRICULUM_VIEW"),
                    new Link("/study-plans", "برنامه‌های مطالعه", "برنامه‌های مطالعه‌ی کلاس‌ها",
                            List.of(ADMIN, TEACHER), List.of()).withPermissions("STUDYPLAN_VIEW"),
                    new Link("/exams", "آزمون‌ها", "ساخت و پیگیری آزمون‌ها",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("EXAM_VIEW"),
                    new Link("/my-exams", "آزمون‌های من", "آزمون‌های شما و نتیجه‌ها",
                            List.of(STUDENT), List.of(STUDENT)),
                    new Link("/my-study-plan", "برنامه‌ی مطالعه‌ی من", "برنامه‌ی مطالعه‌ی شما",
                            List.of(STUDENT), List.of(STUDENT)))),
            new Group("grades", "نمرات", List.of(
                    new Link("/grades", "دفتر نمرات", "ثبت و مرور نمره‌ها",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("GRADES_EDIT"),
                    new Link("/my-grades", "نمره‌های من", "نمره‌های شما",
                            List.of(STUDENT, PARENT), List.of(STUDENT, PARENT)))),
            new Group("finance", "مالی", List.of(
                    new Link("/finance", "امور مالی", "شهریه، پرداخت‌ها و فاکتورها",
                            List.of(ADMIN, FINANCE), List.of(ADMIN, FINANCE)).withPermissions("FINANCE_MANAGE"),
                    new Link("/expenses", "هزینه‌ها", "ثبت و پیگیری هزینه‌ها",
                            List.of(ADMIN, FINANCE), List.of(FINANCE)).withPermissions("EXPENSE_VIEW"),
                    new Link("/my-invoices", "فاکتورهای من", "شهریه و پرداخت‌های شما",
                            List.of(STUDENT, PARENT), List.of(STUDENT, PARENT)))));

    private NavCatalog() {
    }

    /** گروه‌های سایدبار به ترتیب نمایش. */
    public static List<Group> groups() {
        return GROUPS;
    }

    /** لینک‌هایی که حداقل برای یک نقش کارتِ «دسترسی سریع» دارند، به ترتیب کاتالوگ. */
    public static List<Link> shortcuts() {
        List<Link> result = new ArrayList<>();
        for (Group group : GROUPS) {
            for (Link link : group.links()) {
                if (!link.shortcutRoles().isEmpty()) {
                    result.add(link);
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * شرطِ Alpine برای نمایش کلِ بخشِ «دسترسی سریع»: نقشِ انتخاب‌شده حداقل
     * یک کارت داشته باشد (برای کاربر بدون عضویت، که نقشی ندارد، پنهان می‌ماند).
     */
    public static String anyShortcutVisibleWhen(String roleExpression) {
        Set<String> roles = new LinkedHashSet<>();
        shortcuts().forEach(link -> roles.addAll(link.shortcutRoles()));
        return includes(roles, roleExpression);
    }

    private static String includes(java.util.Collection<String> roles, String roleExpression) {
        StringBuilder expression = new StringBuilder("[");
        boolean first = true;
        for (String role : roles) {
            if (!first) {
                expression.append(", ");
            }
            expression.append('\'').append(role).append('\'');
            first = false;
        }
        return expression.append("].includes(").append(roleExpression).append(')').toString();
    }

    /**
     * @param id    کلیدِ پایدار برای state آکاردئون و idهای aria (نه برچسب فارسی)
     * @param label عنوان نمایشی گروه
     */
    public record Group(String id, String label, List<Link> links) {

        /** اجتماعِ نقش‌های لینک‌ها: گروه فقط وقتی دیده می‌شود که یکی از لینک‌هایش دیده شود. */
        public String visibleWhen(String roleExpression) {
            Set<String> roles = new LinkedHashSet<>();
            links.forEach(link -> roles.addAll(link.roles()));
            return includes(roles, roleExpression);
        }

        /** همان، به‌علاوه‌ی اجتماعِ Permissionهای لینک‌ها (نقشِ سفارشی). */
        public String visibleWhen(String roleExpression, String permissionsExpression) {
            Set<String> roles = new LinkedHashSet<>();
            Set<String> permissions = new LinkedHashSet<>();
            links.forEach(link -> {
                roles.addAll(link.roles());
                permissions.addAll(link.permissions());
            });
            return anyOf(includes(roles, roleExpression), permissions, permissionsExpression);
        }
    }

    /**
     * @param roles         نقش‌هایی که لینک را در سایدبار می‌بینند (فقط نمایش)
     * @param shortcutRoles زیرمجموعه‌ی {@code roles} که برایشان کارت «دسترسی سریع» هم هست
     * @param permissions   هر یک از این Permissionها (یا) لینک را برای نقشِ دارای آن هم نمایش می‌دهد -
     *                      برای نقش‌های سفارشی (مثلاً معاونِ دارای TIMETABLE_MANAGE) که در {@code roles}
     *                      نیستند. فقط Permissionِ مدیریتیِ همان صفحه؛ نه Permissionی که نقش‌های
     *                      خودسرویس هم دارند (GRADES_VIEW، FINANCE_VIEW). برای نقش‌های سیستمی باید با
     *                      {@code roles} هم‌خوان باشد (NavCatalogPermissionsFlowIntegrationTest). خالی
     *                      = فقط بر اساس نقش (صفحه‌های «من»). کارتِ «دسترسی سریع» همچنان فقط بر اساس نقش است.
     */
    public record Link(String href, String label, String description,
                       List<String> roles, List<String> shortcutRoles, List<String> permissions) {

        public Link(String href, String label, String description, List<String> roles, List<String> shortcutRoles) {
            this(href, label, description, roles, shortcutRoles, List.of());
        }

        Link withPermissions(String... codes) {
            return new Link(href, label, description, roles, shortcutRoles, List.of(codes));
        }

        /** نمایش در سایدبار: نقشِ انتخاب‌شده در {@code roles} باشد یا Permissionی از {@code permissions} داشته باشد. */
        public String visibleWhen(String roleExpression, String permissionsExpression) {
            return anyOf(includes(roles, roleExpression), permissions, permissionsExpression);
        }

        public String visibleWhen(String roleExpression) {
            return includes(roles, roleExpression);
        }

        public String shortcutVisibleWhen(String roleExpression) {
            return includes(shortcutRoles, roleExpression);
        }
    }

    // «شرطِ نقش || یکی از Permissionها» به‌صورت عبارتِ Alpine؛ بدون Permission همان شرطِ نقش
    private static String anyOf(String roleCondition, java.util.Collection<String> permissions,
                                String permissionsExpression) {
        if (permissions.isEmpty()) {
            return roleCondition;
        }
        StringBuilder codes = new StringBuilder("[");
        boolean first = true;
        for (String code : permissions) {
            if (!first) {
                codes.append(", ");
            }
            codes.append('\'').append(code).append('\'');
            first = false;
        }
        codes.append(']');
        return roleCondition + " || " + codes + ".some(p => " + permissionsExpression + ".includes(p))";
    }
}
