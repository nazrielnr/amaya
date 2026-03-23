import { AntigravityHttpCore } from './AntigravityHttpCore';
import { ANTIGRAVITY_ENDPOINTS } from './AntigravityProtocol';

export class AntigravityRpcClient {
    constructor(private readonly httpCore: AntigravityHttpCore) {}

    getMetadata(): any {
        return this.httpCore.getMetadata();
    }

    startCascade(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.startCascade, payload);
    }

    sendUserCascadeMessage(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.sendUserCascadeMessage, payload);
    }

    cancelCascadeInvocation(cascadeId: string): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.cancelCascadeInvocation, { cascadeId });
    }

    getCascadeTrajectory(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.getCascadeTrajectory, payload);
    }

    getUserStatus(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.getUserStatus, payload);
    }

    getAllCascadeTrajectories(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.getAllCascadeTrajectories, payload);
    }

    getUserTrajectoryDescriptions(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.getUserTrajectoryDescriptions, payload);
    }

    getCascadeNuxes(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.getCascadeNuxes, payload);
    }

    handleCascadeUserInteraction(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.handleCascadeUserInteraction, payload);
    }

    saveMediaAsArtifact(payload: any): Promise<any> {
        return this.httpCore.callEndpoint(ANTIGRAVITY_ENDPOINTS.saveMediaAsArtifact, payload);
    }
}
