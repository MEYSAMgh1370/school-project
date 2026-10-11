// رندر فرمول‌های TeX درون متن سؤال با KaTeX (O2). متن در پایگاه داده همان TeX است که
// داخل `$…$` می‌آید؛ مرورگر آن را می‌سازد و هیچ HTML دلخواهی درج نمی‌شود. `\$` یعنی علامت
// دلار معمولی؛ `$` بدون بستن هم به‌صورت متن نمایش داده می‌شود. `$$` پشتیبانی نمی‌شود.
(function () {
    function splitSegments(text) {
        const segments = [];
        let plain = '';
        let i = 0;
        while (i < text.length) {
            const ch = text[i];
            if (ch === '\\' && text[i + 1] === '$') {
                plain += '$';
                i += 2;
                continue;
            }
            if (ch === '$') {
                const close = findClosingDollar(text, i + 1);
                if (close !== -1) {
                    if (plain) segments.push({ tex: false, value: plain });
                    plain = '';
                    segments.push({ tex: true, value: text.slice(i + 1, close) });
                    i = close + 1;
                    continue;
                }
            }
            plain += ch;
            i += 1;
        }
        if (plain) segments.push({ tex: false, value: plain });
        return segments;
    }

    function findClosingDollar(text, from) {
        for (let j = from; j < text.length; j++) {
            if (text[j] === '\\' && text[j + 1] === '$') {
                j += 1;
                continue;
            }
            if (text[j] === '$') return j;
        }
        return -1;
    }

    function renderMath(el, text) {
        el.textContent = '';
        for (const segment of splitSegments(String(text ?? ''))) {
            if (!segment.tex) {
                el.appendChild(document.createTextNode(segment.value));
                continue;
            }
            const holder = document.createElement('span');
            if (window.katex) {
                window.katex.render(segment.value, holder, { throwOnError: false, trust: false, displayMode: false });
            } else {
                holder.textContent = segment.value;
            }
            el.appendChild(holder);
        }
    }

    document.addEventListener('alpine:init', () => {
        Alpine.directive('math', (el, { expression }, { evaluateLater, effect }) => {
            const evaluate = evaluateLater(expression);
            effect(() => evaluate((text) => renderMath(el, text)));
        });
    });
})();
