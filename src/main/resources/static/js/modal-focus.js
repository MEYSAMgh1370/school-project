(function () {
    const focusableSelector = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])'
    ].join(',');

    function focusableElements(root) {
        if (!root) {
            return [];
        }
        return [...root.querySelectorAll(focusableSelector)]
            .filter(element => element.getClientRects().length > 0 && element.getAttribute('aria-hidden') !== 'true');
    }

    window.ModalFocus = {
        focusFirst(root, preferred) {
            const target = preferred && preferred.getClientRects().length > 0
                ? preferred
                : focusableElements(root)[0] || root;
            target?.focus();
        },

        trap(event, root) {
            const elements = focusableElements(root);
            if (elements.length === 0) {
                event.preventDefault();
                root?.focus();
                return;
            }
            const first = elements[0];
            const last = elements[elements.length - 1];
            if (event.shiftKey && (document.activeElement === first || !root.contains(document.activeElement))) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && (document.activeElement === last || !root.contains(document.activeElement))) {
                event.preventDefault();
                first.focus();
            }
        },

        restore(opener, fallback) {
            window.requestAnimationFrame(() => {
                const target = opener?.isConnected ? opener : fallback;
                target?.focus();
            });
        }
    };
})();
