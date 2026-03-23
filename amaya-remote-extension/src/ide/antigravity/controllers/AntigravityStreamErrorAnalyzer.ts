export class AntigravityStreamErrorAnalyzer {
    public static extractFailureMessage(steps: Map<number, any>, fallback: string): string {
        const needle = /\b(exhausted|quota|capacity|overages|baseline\s+model\s+quota)\b/i;

        const fromStructuredError = (step: any): string | null => {
            try {
                const err = step?.errorMessage?.error;
                const user = err?.userErrorMessage;
                if (typeof user === 'string' && user.trim()) return user.trim();

                const shortError = err?.shortError;
                if (typeof shortError === 'string' && shortError.trim()) return shortError.trim();

                const modelError = err?.modelErrorMessage;
                if (typeof modelError === 'string' && modelError.trim()) return modelError.trim();

                const fullError = err?.fullError;
                if (typeof fullError === 'string' && fullError.trim()) return fullError.trim();
            } catch {
                // ignore
            }
            return null;
        };

        const convertResetTime = (text: string): string => {
            const re = /(reset\s+after\s+)([0-9dhms]+)\b/i;
            const m = text.match(re);
            if (!m) return text;

            const dur = m[2];
            let totalMs = 0;
            const parts = Array.from(dur.matchAll(/(\d+)([dhms])/gi));
            for (const p of parts) {
                const v = parseInt(p[1], 10);
                const u = (p[2] || '').toLowerCase();
                if (isNaN(v)) continue;
                if (u === 'd') totalMs += v * 24 * 60 * 60 * 1000;
                if (u === 'h') totalMs += v * 60 * 60 * 1000;
                if (u === 'm') totalMs += v * 60 * 1000;
                if (u === 's') totalMs += v * 1000;
            }

            if (totalMs > 0) {
                const dt = new Date(Date.now() + totalMs);
                const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                const formatted = `${pad(dt.getDate())}/${pad(dt.getMonth() + 1)}/${dt.getFullYear()} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
                return text.replace(re, `reset at ${formatted}`);
            }
            return text;
        };

        const convertAltTime = (text: string): string => {
            const altRe = /(\d{2}-\d{2}-\d{2}t\d{2}:\d{2}:\d{2}z)/gi;
            return text.replace(altRe, (altStr: string) => {
                try {
                    const parts = altStr.toLowerCase().split('t')[0].split('-');
                    const day = parseInt(parts[0], 10);
                    const month = parseInt(parts[1], 10);
                    let year = parseInt(parts[2], 10);
                    if (year < 100) year += 2000;

                    const timeParts = altStr.toLowerCase().split('t')[1].replace('z', '').split(':');
                    const hours = parseInt(timeParts[0], 10);
                    const minutes = parseInt(timeParts[1], 10);

                    const date = new Date(year, month - 1, day, hours, minutes);
                    const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
                    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
                } catch {
                    return altStr;
                }
            });
        };

        try {
            const indices = Array.from(steps.keys()).sort((a, b) => a - b);
            for (let i = indices.length - 1; i >= 0; i--) {
                const s = steps.get(indices[i]);
                if (!s) continue;

                const structured = fromStructuredError(s);
                if (structured) return convertAltTime(convertResetTime(structured));

                const pr = s?.plannerResponse;
                const prMsg = (pr?.modifiedResponse || pr?.response || '').toString();
                if (prMsg && needle.test(prMsg)) return convertAltTime(convertResetTime(prMsg));

                const raw = JSON.stringify(s);
                const m = raw.match(needle);
                if (m && m.index !== undefined) {
                    const start = Math.max(0, m.index - 120);
                    const end = Math.min(raw.length, m.index + 380);
                    return convertAltTime(convertResetTime(raw.substring(start, end)));
                }
            }

            for (let i = indices.length - 1; i >= 0; i--) {
                const s = steps.get(indices[i]);
                const structured = fromStructuredError(s);
                if (structured) return convertAltTime(convertResetTime(structured));
                const pr = s?.plannerResponse;
                const msg = (pr?.modifiedResponse || pr?.response || '').toString();
                if (msg) return convertAltTime(convertResetTime(msg));
            }
        } catch {
            // ignore
        }
        return convertAltTime(convertResetTime(fallback)) || 'Cascade failed';
    }

    public static isQuotaErrorText(text: string): boolean {
        if (!text) return false;
        return /exhausted\s+your\s+capacity/i.test(text)
            || /baseline\s+model\s+quota\s+reached/i.test(text)
            || /quota\s+(will\s+)?reset/i.test(text)
            || /(capacity|quota)\s+reached/i.test(text);
    }

    public static isLikelyErrorMessage(text: string): boolean {
        if (!text) return false;
        return this.isQuotaErrorText(text)
            || /resource_exhausted/i.test(text)
            || /http\s*42\d\b/i.test(text)
            || /http\s*5\d\d\b/i.test(text)
            || /\b(unauthorized|forbidden|rate\s*limit|timeout|socket\s+hang\s+up)\b/i.test(text)
            || /\b(connection\s+unstable|too\s+many\s+disconnections|request\s+failed|stream\s+failed|request\s+canceled|request\s+cancelled)\b/i.test(text);
    }
}