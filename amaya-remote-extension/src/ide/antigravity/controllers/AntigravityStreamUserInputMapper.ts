import * as fs from 'fs';
import { ANTIGRAVITY_STEP_TYPES } from '../core/AntigravityProtocol';

export type StreamUserAttachment = {
    mimeType: string;
    dataBase64?: string;
    fileName?: string;
    uri?: string;
};

export class AntigravityStreamUserInputMapper {
    public static extractFromStep(step: any): { text: string | null; attachments: StreamUserAttachment[] } {
        if (step?.type !== ANTIGRAVITY_STEP_TYPES.userInput) {
            return { text: null, attachments: [] };
        }

        const userInput = step.userInput;
        const items = userInput?.items || [];
        const text = items
            .map((item: any) => item?.text || '')
            .find((value: string) => value && value.trim()) || null;

        const attachments: StreamUserAttachment[] = [];
        const mediaArray = userInput?.media || [];
        mediaArray.forEach((media: any) => {
            if (!media?.mimeType || (!media?.uri && !media?.inlineData)) return;

            let dataBase64 = media.inlineData;

            if (!dataBase64 && media.uri) {
                try {
                    const filePath = String(media.uri).replace(/^file:\/\/\//, '').replace(/\//g, '\\');
                    if (fs.existsSync(filePath)) {
                        const buffer = fs.readFileSync(filePath);
                        dataBase64 = buffer.toString('base64');
                    }
                } catch {
                    // ignore fallback failures
                }
            }

            attachments.push({
                mimeType: media.mimeType,
                dataBase64,
                fileName: media.fileName,
                uri: media.uri,
            });
        });

        return { text: text ? text.trim() : null, attachments };
    }
}