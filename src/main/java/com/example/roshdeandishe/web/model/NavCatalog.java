package com.example.roshdeandishe.web.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * منبع واحدِ لینک‌های صفحه‌های مدیریتی: هم سایدبارِ {@code layout/navbar.jte}
 * و هم کارت‌های «دسترسی سریع» پیشخوان از همین فهرست رندر می‌شوند، تا لینک
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

    /** نقش‌های سیستمی: کارتِ «دسترسی سریع» برایشان فقط بر اساس نقش است، نه Permission (فقط نقش سفارشی Permission-محور است). */
    private static final List<String> SYSTEM_ROLES = List.of(ADMIN, TEACHER, STUDENT, PARENT, FINANCE);

    private static final List<Group> GROUPS = List.of(
            new Group("school", "مدرسه", List.of(
                    new Link("/people", "اعضای مدرسه", "افزودن و مدیریت اعضای مدرسه",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("MEMBERSHIP_MANAGE"),
                    new Link("/students", "دانش‌آموزان", "فهرست دانش‌آموزان مدرسه",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("STUDENT_ENROLL", "STUDENT_VIEW"),
                    new Link("/roles", "نقش‌ها و دسترسی‌ها", "تعریف نقش‌ها و دسترسی‌هایشان",
                            List.of(ADMIN), List.of()).withPermissions("ROLE_MANAGE"),
                    new Link("/academic-year", "سال تحصیلی", "انتخاب سال تحصیلی جاریِ مدرسه",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("ACADEMIC_YEAR_MANAGE"),
                    new Link("/promotion", "ارتقای پایه", "ارتقای دانش‌آموزان به سال جدید و جا دادن در کلاس‌ها",
                            List.of(ADMIN), List.of()).withPermissions("ACADEMIC_YEAR_MANAGE"),
                    new Link("/calendar", "تقویم مدرسه", "روزهای تعطیل مدرسه؛ در این روزها حضور و غیاب گرفته نمی‌شود",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("CALENDAR_MANAGE"))),
            new Group("education", "آموزش", List.of(
                    new Link("/classes", "کلاس‌ها", "کلاس‌ها و ثبت‌نام دانش‌آموزان",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("CLASS_VIEW"),
                    new Link("/timetable", "برنامه‌ی کلاسی", "زنگ‌ها و برنامه‌ی هفتگیِ کلاس‌ها",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("TIMETABLE_MANAGE"),
                    new Link("/my-timetable", "برنامه‌ی کلاسی من", "برنامه‌ی هفتگیِ شما",
                            List.of(STUDENT, PARENT, TEACHER), List.of(STUDENT, PARENT, TEACHER)),
                    // خودسرویسِ حضور و غیاب: دانش‌آموز غیبت/تأخیرِ خودش را می‌بیند و والد برای فرزندش عذر می‌فرستد
                    new Link("/my-attendance", "غیبت‌های من", "غیبت و تأخیرِ شما یا فرزندانتان و ارسال عذر",
                            List.of(STUDENT, PARENT), List.of(STUDENT, PARENT)),
                    // ثبت: «یکی از دو مجوز» (معلم ATTENDANCE_RECORD، مدیر ATTENDANCE_RECORD_ANY)
                    new Link("/attendance", "حضور و غیاب", "ثبت حضور و غیاب کلاس‌ها",
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER))
                            .withPermissions("ATTENDANCE_RECORD").orPermissions("ATTENDANCE_RECORD_ANY"),
                    new Link("/attendance/report", "گزارش حضور و غیاب", "غایبین روز و گزارش بازه‌ای",
                            List.of(ADMIN), List.of(ADMIN)).withPermissions("ATTENDANCE_VIEW_ALL"),
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
                            List.of(ADMIN, TEACHER), List.of(ADMIN, TEACHER)).withPermissions("GRADES_EDIT", "CLASS_VIEW"),
                    new Link("/my-grades", "نمره‌های من", "نمره‌های شما",
                            List.of(STUDENT, PARENT), List.of(STUDENT, PARENT)))),
            new Group("finance", "مالی", List.of(
                    new Link("/finance", "امور مالی", "شهریه، پرداخت‌ها و فاکتورها",
                            List.of(ADMIN, FINANCE), List.of(ADMIN, FINANCE)).withPermissions("FINANCE_MANAGE", "FINANCE_VIEW"),
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

    /**
     * همان، به‌علاوه‌ی «نقشِ سفارشی‌ای که همه‌ی Permissionهای یکی از کارت‌ها را دارد».
     */
    public static String anyShortcutVisibleWhen(String roleExpression, String permissionsExpression) {
        Set<String> roles = new LinkedHashSet<>();
        Set<List<String>> permissionSets = new LinkedHashSet<>();
        shortcuts().forEach(link -> {
            roles.addAll(link.shortcutRoles());
            permissionSets.addAll(link.permissionSets());
        });
        return anyOf(includes(roles, roleExpression), permissionSets, customRolePermissions(roleExpression,
                permissionsExpression));
    }

    // نقشِ سفارشی = نقشی که در SYSTEM_ROLES نیست: نقش‌های سیستمی کارت‌هایشان را فقط از shortcutRoles می‌گیرند
    // (مثلاً مدیر مدرسه Permissionِ EXPENSE_VIEW را دارد ولی کارتِ «هزینه‌ها» برای او نیست)
    private static String customRolePermissions(String roleExpression, String permissionsExpression) {
        return "(!" + includes(SYSTEM_ROLES, roleExpression) + " ? " + permissionsExpression + " : [])";
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

        /** همان، به‌علاوه‌ی «همه‌ی Permissionهای یکی از لینک‌ها» (نقشِ سفارشی). */
        public String visibleWhen(String roleExpression, String permissionsExpression) {
            Set<String> roles = new LinkedHashSet<>();
            Set<List<String>> permissionSets = new LinkedHashSet<>();
            links.forEach(link -> {
                roles.addAll(link.roles());
                permissionSets.addAll(link.permissionSets());
            });
            return anyOf(includes(roles, roleExpression), permissionSets, permissionsExpression);
        }
    }

    /**
     * @param roles         نقش‌هایی که لینک را در سایدبار می‌بینند (فقط نمایش)
     * @param shortcutRoles زیرمجموعه‌ی {@code roles} که برایشان کارت «دسترسی سریع» هم هست
     * @param permissions   <b>همه‌ی</b> این Permissionها را که نقشِ انتخاب‌شده داشته باشد لینک را برایش
     *                      نمایش می‌دهد - برای نقش‌های سفارشی (مثلاً معاونِ دارای TIMETABLE_MANAGE) که در
     *                      {@code roles} نیستند. مجموعه = Permissionِ مدیریتیِ صفحه + Permissionی که
     *                      بارگذاریِ اولیه‌ی <i>مسدودکننده</i>ی صفحه لازم دارد (Permissionها مستقل انتخاب
     *                      می‌شوند: FINANCE_MANAGE بدون FINANCE_VIEW صفحه‌ای می‌دهد که با اولین 403 بسته
     *                      می‌شود، و STUDENT_ENROLL بدون STUDENT_VIEW هم). نه Permissionی که نقش‌های
     *                      خودسرویس هم دارند، مگر در ترکیبی که فقط نقش‌های همان لینک دارند (GRADES_VIEW،
     *                      FINANCE_VIEW تنها). برای نقش‌های سیستمی باید با {@code roles} هم‌خوان باشد
     *                      (NavPermissionsFlowIntegrationTest). خالی = فقط بر اساس نقش (صفحه‌های «من»).
     *                      کارتِ «دسترسی سریع» همچنان فقط بر اساس نقش است.
     * @param alternativePermissions مجموعه‌های جایگزین: صفحه‌ای که «یکی از چند مجوز» می‌پذیرد (ثبت حضور و
     *                      غیاب: معلم ATTENDANCE_RECORD یا مدیر ATTENDANCE_RECORD_ANY) به‌ازای هر جایگزین یک
     *                      مجموعه‌ی «همه» دارد؛ لینک وقتی دیده می‌شود که همه‌ی {@code permissions} <b>یا</b> همه‌ی
     *                      یکی از جایگزین‌ها را نقش داشته باشد. معمولاً خالی است.
     */
    public record Link(String href, String label, String description,
                       List<String> roles, List<String> shortcutRoles, List<String> permissions,
                       List<List<String>> alternativePermissions) {

        public Link(String href, String label, String description, List<String> roles, List<String> shortcutRoles) {
            this(href, label, description, roles, shortcutRoles, List.of(), List.of());
        }

        public Link(String href, String label, String description,
                    List<String> roles, List<String> shortcutRoles, List<String> permissions) {
            this(href, label, description, roles, shortcutRoles, permissions, List.of());
        }

        Link withPermissions(String... codes) {
            return new Link(href, label, description, roles, shortcutRoles, List.of(codes), alternativePermissions);
        }

        /** یک مجموعه‌ی جایگزین (همه‌ی این مجوزها) کنارِ {@code permissions}؛ «یا» بین مجموعه‌ها. */
        Link orPermissions(String... codes) {
            List<List<String>> alternatives = new ArrayList<>(alternativePermissions);
            alternatives.add(List.of(codes));
            return new Link(href, label, description, roles, shortcutRoles, permissions, List.copyOf(alternatives));
        }

        /** {@code permissions} و جایگزین‌ها، هرکدام یک مجموعه‌ی «همه»؛ مجموعه‌ی خالی نادیده گرفته می‌شود. */
        public List<List<String>> permissionSets() {
            List<List<String>> sets = new ArrayList<>();
            sets.add(permissions);
            sets.addAll(alternativePermissions);
            return List.copyOf(sets);
        }

        /** نمایش در سایدبار: نقشِ انتخاب‌شده در {@code roles} باشد یا همه‌ی {@code permissions} (یا یک جایگزین) را داشته باشد. */
        public String visibleWhen(String roleExpression, String permissionsExpression) {
            return anyOf(includes(roles, roleExpression), permissionSets(), permissionsExpression);
        }

        public String visibleWhen(String roleExpression) {
            return includes(roles, roleExpression);
        }

        public String shortcutVisibleWhen(String roleExpression) {
            return includes(shortcutRoles, roleExpression);
        }

        /**
         * کارت «دسترسی سریع»: نقشِ انتخاب‌شده در {@code shortcutRoles} باشد، یا نقشِ <b>سفارشی</b> باشد و همه‌ی
         * {@code permissions} را داشته باشد (همان قاعده‌ی سایدبار؛ نقش‌های سیستمی همچنان فقط بر اساس نقش).
         */
        public String shortcutVisibleWhen(String roleExpression, String permissionsExpression) {
            return anyOf(includes(shortcutRoles, roleExpression), permissionSets(),
                    customRolePermissions(roleExpression, permissionsExpression));
        }
    }

    // «شرطِ نقش || داشتنِ همه‌ی Permissionهای یک لینک (برای هر لینک)» به‌صورت عبارتِ Alpine؛ بدون
    // Permission همان شرطِ نقش
    private static String anyOf(String roleCondition, java.util.Collection<List<String>> permissionSets,
                                String permissionsExpression) {
        StringBuilder expression = new StringBuilder(roleCondition);
        for (List<String> codes : permissionSets) {
            if (codes.isEmpty()) {
                continue;
            }
            StringBuilder list = new StringBuilder("[");
            boolean first = true;
            for (String code : codes) {
                if (!first) {
                    list.append(", ");
                }
                list.append('\'').append(code).append('\'');
                first = false;
            }
            list.append(']');
            expression.append(" || ").append(list).append(".every(p => ").append(permissionsExpression)
                    .append(".includes(p))");
        }
        return expression.toString();
    }
}
