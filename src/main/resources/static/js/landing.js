/* اسلایدر صفحه‌ی اول عمومی. تنها کامپوننت تعاملی این صفحه است و فقط
   همین‌جا به کار می‌رود (DESIGN.md §5) - صفحه‌های داخلی برنامه اسلایدر
   ندارند.

   اسلایدها در قالب سرور رندر می‌شوند و x-cloak ندارند: اگر Alpine بالا
   نیاید، هر چهار اسلاید پشت سر هم و خوانا نمایش داده می‌شوند. */
document.addEventListener('alpine:init', () => {
    Alpine.data('landingSlider', (slideCount) => ({
        count: slideCount,
        active: 0,
        playing: false,
        timer: null,
        delay: 7000,

        init() {
            // چرخش خودکار برای کسی که کاهش حرکت را خواسته اجرا نمی‌شود؛
            // قانون سراسری app.css فقط انیمیشن CSS را می‌گیرد، نه
            // تایمر جاوااسکریپت را.
            const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            if (!reducedMotion && this.count > 1) {
                this.play();
            }
        },

        destroy() {
            this.pause();
        },

        play() {
            if (this.playing) return;
            this.playing = true;
            this.timer = setInterval(() => this.go(this.active + 1), this.delay);
        },

        pause() {
            if (this.timer !== null) {
                clearInterval(this.timer);
                this.timer = null;
            }
            this.playing = false;
        },

        go(index) {
            this.active = (index + this.count) % this.count;
        },

        // انتخاب دستی، چرخش خودکار را متوقف می‌کند تا اسلاید زیر دست
        // کاربر عوض نشود.
        select(index) {
            this.pause();
            this.go(index);
        },

        next() {
            this.select(this.active + 1);
        },

        previous() {
            this.select(this.active - 1);
        }
    }));
});
