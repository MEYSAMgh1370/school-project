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
# JVM سقف heap را از cgroup کانتینر می‌خواند - بدون این، روی پلن‌های کم‌حافظه‌ی
# لیارا یا زیادی heap برمی‌دارد یا زیادی محافظه‌کار می‌شود.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
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
