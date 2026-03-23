export interface IIDECommandExecutor {
    confirmAction(confirmed: boolean): Promise<void>;
}
