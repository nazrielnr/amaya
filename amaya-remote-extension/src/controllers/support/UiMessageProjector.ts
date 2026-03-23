export class UiMessageProjector {
    safeParseJson(str: string): Record<string, any> {
        try { return JSON.parse(str); }
        catch { return {}; }
    }

    mapMessagesForUi(messages: any[]): any[] {
        return messages.map(m => ({
            role: m.role,
            content: m.content || '',
            thinking: m.thinking,
            intent: m.intent,
            metadata: m.metadata || {},
            toolExecutions: (() => {
                const raw = (m.toolCalls || m.toolExecutions || []) as any[];
                const deduped = new Map<string, any>();
                for (const tc of raw) {
                    const id = tc?.id || tc?.toolCallId;
                    if (!id) continue;
                    const mapped = {
                        toolCallId: id,
                        name: tc.name,
                        arguments: tc.args
                            ? (typeof tc.args === 'string' ? this.safeParseJson(tc.args) : (tc.args || {}))
                            : (tc.arguments || {}),
                        result: typeof tc.result === 'string' ? tc.result : (tc.output || null) || '',
                        isError: tc.isError || false,
                        status: tc.status || (tc.result ? (tc.isError ? 'ERROR' : 'SUCCESS') : 'RUNNING'),
                        metadata: tc.metadata || {},
                    };
                    if (deduped.has(id)) deduped.delete(id);
                    deduped.set(id, mapped);
                }
                return Array.from(deduped.values());
            })(),
            attachments: this.extractAttachments(m)
        }));
    }

    private extractAttachments(m: any): any[] {
        const attachments: any[] = [];

        if (Array.isArray(m.attachments)) {
            for (const a of m.attachments) {
                if (a?.mimeType && (a.dataBase64 || a.inlineData)) {
                    attachments.push({
                        mimeType: a.mimeType,
                        dataBase64: a.dataBase64 || a.inlineData,
                        fileName: a.fileName || ''
                    });
                }
            }
        }

        const mediaArray = m?.userInput?.media || m?.media || [];
        if (Array.isArray(mediaArray)) {
            for (const media of mediaArray) {
                if (media?.mimeType && (media.inlineData || media.uri)) {
                    attachments.push({
                        mimeType: media.mimeType,
                        dataBase64: media.inlineData || '',
                        fileName: media.fileName || '',
                        uri: media.uri || ''
                    });
                }
            }
        }

        const items = m?.userInput?.items || m?.items || [];
        if (Array.isArray(items)) {
            for (const item of items) {
                if (item?.mimeType && (item.inlineData || item.uri)) {
                    attachments.push({
                        mimeType: item.mimeType,
                        dataBase64: item.inlineData || '',
                        fileName: item.fileName || '',
                        uri: item.uri || ''
                    });
                }
            }
        }

        return attachments;
    }
}
