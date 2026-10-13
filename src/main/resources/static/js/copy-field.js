// «کپی» برای یک فیلدِ readonly (ایمیلِ حساب روی پیشخوانِ کاربرِ بدونِ مدرسه). بدونِ دسترسی به clipboard
// متن انتخاب می‌شود تا کاربر دستی کپی کند.
document.addEventListener('alpine:init', () => {
    Alpine.data('copyField', () => ({
        copied: false,

        async copy() {
            const input = this.$refs.field;
            input.select();
            try {
                await navigator.clipboard.writeText(input.value);
                this.copied = true;
                setTimeout(() => { this.copied = false; }, 2000);
            } catch (e) {
                // انتخاب‌شده می‌ماند؛ کاربر با میان‌بر کپی می‌کند.
            }
        }
    }));
});
