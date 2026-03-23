import { IDEMessageAttachment } from '../../../interfaces/IIDEClient';
import { AntigravityHttpCore } from '../core/AntigravityHttpCore';
import { AntigravityRpcClient } from '../core/AntigravityRpcClient';

type OutboundMedia = {
    mimeType: string;
    inlineData?: string;
    mediaArtifactUri?: string;
};

const ARTIFACT_UPLOAD_THRESHOLD = 200_000;

export class AntigravityMediaController {
    public static async buildOutboundMedia(
        attachments: IDEMessageAttachment[],
        httpCore: AntigravityHttpCore
    ): Promise<OutboundMedia[]> {
        const media: OutboundMedia[] = [];

        for (let index = 0; index < attachments.length; index++) {
            const attachment = attachments[index];
            let mimeType = String(attachment.mimeType || '').trim();
            const inlineData = this.normalizeAttachmentBase64(attachment.dataBase64);
            if (!mimeType || !inlineData) {
                console.warn(`[AntigravityMediaController] Attachment ${index + 1} dropped: mimeType=${!!mimeType}, hasData=${!!inlineData}`);
                continue;
            }

            if (mimeType === 'image/*' || !mimeType.startsWith('image/')) {
                const detected = this.detectImageMimeType(inlineData);
                if (detected) {
                    console.log(`[AntigravityMediaController] Detected image format: ${detected} (was: ${mimeType})`);
                    mimeType = detected;
                } else {
                    mimeType = 'image/jpeg';
                    console.log(`[AntigravityMediaController] Could not detect format, defaulting to ${mimeType}`);
                }
            }

            const shouldUseArtifact = inlineData.length > ARTIFACT_UPLOAD_THRESHOLD;
            console.log(`[AntigravityMediaController] Attachment ${index + 1}: ${mimeType}, ${inlineData.length} chars, shouldUseArtifact=${shouldUseArtifact}`);

            if (shouldUseArtifact) {
                const mediaArtifactUri = await this.tryUploadArtifact(httpCore, mimeType, inlineData);
                if (mediaArtifactUri) {
                    media.push({ mimeType, mediaArtifactUri });
                    continue;
                }
            }

            media.push({ mimeType, inlineData });
        }

        return media;
    }

    private static normalizeAttachmentBase64(dataBase64: string): string {
        const trimmed = String(dataBase64 || '').trim();
        if (!trimmed) return '';
        if (!trimmed.startsWith('data:')) return trimmed;
        const commaIndex = trimmed.indexOf(',');
        return commaIndex >= 0 ? trimmed.substring(commaIndex + 1) : trimmed;
    }

    private static detectImageMimeType(base64Data: string): string | null {
        try {
            const buffer = Buffer.from(base64Data.slice(0, 16), 'base64');
            if (buffer.length < 4) return null;

            if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) {
                return 'image/png';
            }
            if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
                return 'image/jpeg';
            }
            if (buffer[0] === 0x47 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x38) {
                return 'image/gif';
            }
            if (buffer.length >= 12 && buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
                buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50) {
                return 'image/webp';
            }
            return null;
        } catch {
            return null;
        }
    }

    private static extractMediaArtifactUri(res: any): string {
        if (!res || typeof res !== 'object') return '';
        return String(
            res.mediaArtifactUri
            || res.artifactUri
            || res.uri
            || res.media?.uri
            || res.artifact?.uri
            || ''
        ).trim();
    }

    private static async tryUploadArtifact(httpCore: AntigravityHttpCore, mimeType: string, inlineData: string): Promise<string> {
        try {
            const rpc = new AntigravityRpcClient(httpCore);
            console.log(`[AntigravityMediaController] Attempting artifact upload for large image (${inlineData.length} chars)`);
            const res = await rpc.saveMediaAsArtifact({
                media: {
                    mimeType,
                    inlineData,
                },
            });
            const uri = this.extractMediaArtifactUri(res);
            if (uri) {
                console.log(`[AntigravityMediaController] Artifact upload succeeded, uri: ${uri.substring(0, 50)}...`);
                return uri;
            }
            console.warn('[AntigravityMediaController] Artifact upload returned empty uri, falling back to inline');
            return '';
        } catch (e: any) {
            console.error(`[AntigravityMediaController] Artifact upload failed: ${e.message}, falling back to inline`);
            return '';
        }
    }
}