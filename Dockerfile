# ---------- مرحله‌ی build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# pom.xml جدا از src کپی می‌شود تا تا وقتی وابستگی‌ها عوض نشده‌اند،
# build بعدی دوباره کل مخزن Maven را دانلود نکند.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# تست‌ها عمداً skip می‌شوند: تست‌های این پروژه Testcontainers هستند و به
# دسترسیِ Docker-in-Docker نیاز دارند که در محیط build لیارا وجود ندارد.
# اجرای تست‌ها جای خودش (لوکال / CI) است، نه مسیر استقرار.
RUN mvn -B -ntp -DskipTests package

# ---------- مرحله‌ی runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl فقط برای اینکه دستور healthCheck در liara.json چیزی برای اجرا داشته
# باشد؛ ایمیج پایه‌ی JRE آن را ندارد.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# دیتای این سامانه تقویم شمسی/زمان ایران است؛ کانتینر پیش‌فرض UTC است.
ENV TZ=Asia/Tehran
# تا پیش از این، سقف heap به صورت درصدی از حافظه‌ی cgroup داده می‌شد
# (MaxRAMPercentage=75) تا JVM خودش را با پلن کانتینر وفق دهد. اندازه‌گیری نشان
# داد که آن درصد روی یک کانتینر ۵۱۲ مگابایتی حدود ۱۹۶ مگابایت heap را commit
# می‌کند، در حالی که live set واقعی این اپلیکیشن پس از یک full GC حدود ۵۴
# مگابایت است - یعنی درصد، heap را به اندازه‌ی پلن بزرگ می‌کند نه به اندازه‌ی
# نیاز برنامه. سقف صریح، همان وفق‌دادن را بدون آن هدررفت انجام می‌دهد.
# فاصله‌ی ۱۹۲ تا ۵۴ عمدی است: session ها در حافظه‌ی همین کانتینر نگه داشته
# می‌شوند (رجوع به DEPLOYMENT.md، «Known limitations») و پیوست هزینه تا ۵
# مگابایت به صورت byte[] وارد heap می‌شود، پس headroom لازم است.
#
# SerialGC: این استقرار تک-instance با heap کوچک و ترافیک پایین است - دقیقاً
# همان حالتی که G1 در آن صرفه ندارد. G1 حدود ۴۶ مگابایت ساختار داخلی commit
# می‌کرد که با SerialGC به کمتر از یک مگابایت رسید.
#
# Xss512k و ReservedCodeCacheSize: هر دو سقف‌های پیش‌فرضِ سخاوتمندانه‌ای‌اند که
# این اپلیکیشن به آن‌ها نمی‌رسد.
#
# پیش از تغییر این مقادیر، رجوع به openspec/changes/production-memory-footprint.md
# که روش اندازه‌گیری، باند خطا، و اینکه baseline روی دیتابیس خالی گرفته شده را
# ثبت کرده است.
ENV JAVA_OPTS="-Xmx192m -Xms64m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m"
# پروفایل production عمداً داخل خود image باک شده و به یک متغیر در پنل واگذار
# نشده: فراموش کردنش یک اشتباه کوچک نیست. بدون آن، seed داده‌ی نمایشی اجرا
# می‌شود (یک SCHOOL_ADMIN عمومی با رمز معلوم) و کلیدهای امضای dev که در گیت
# هستند به قوت خود باقی می‌مانند. فقط وقتی این image را عامدانه غیرproduction
# اجرا می‌کنید، با -e SPRING_PROFILES_ACTIVE=... بازنویسی‌اش کنید.
ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=build /workspace/target/roshdeandishe-*.jar /app/app.jar

RUN useradd --uid 1001 app && chown app:app /app/app.jar
USER app

EXPOSE 8080
# exec تا JVM خودش PID 1 شود و SIGTERM لیارا به graceful shutdown اسپرینگ برسد.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
