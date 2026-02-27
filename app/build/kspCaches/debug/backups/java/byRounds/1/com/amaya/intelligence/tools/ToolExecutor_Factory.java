package com.amaya.intelligence.tools;

import com.amaya.intelligence.domain.security.CommandValidator;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ToolExecutor_Factory implements Factory<ToolExecutor> {
  private final Provider<ListFilesTool> listFilesToolProvider;

  private final Provider<ReadFileTool> readFileToolProvider;

  private final Provider<WriteFileTool> writeFileToolProvider;

  private final Provider<CreateDirectoryTool> createDirectoryToolProvider;

  private final Provider<DeleteFileTool> deleteFileToolProvider;

  private final Provider<RunShellTool> runShellToolProvider;

  private final Provider<CopyFileTool> copyFileToolProvider;

  private final Provider<MoveFileTool> moveFileToolProvider;

  private final Provider<SearchFilesTool> searchFilesToolProvider;

  private final Provider<EditFileTool> editFileToolProvider;

  private final Provider<GetFileInfoTool> getFileInfoToolProvider;

  private final Provider<FindFilesTool> findFilesToolProvider;

  private final Provider<UndoChangeTool> undoChangeToolProvider;

  private final Provider<BatchReadTool> batchReadToolProvider;

  private final Provider<ApplyDiffTool> applyDiffToolProvider;

  private final Provider<CreateReminderTool> createReminderToolProvider;

  private final Provider<UpdateMemoryTool> updateMemoryToolProvider;

  private final Provider<UpdateTodoTool> updateTodoToolProvider;

  private final Provider<InvokeSubagentsTool> invokeSubagentsToolProvider;

  private final Provider<CommandValidator> commandValidatorProvider;

  public ToolExecutor_Factory(Provider<ListFilesTool> listFilesToolProvider,
      Provider<ReadFileTool> readFileToolProvider, Provider<WriteFileTool> writeFileToolProvider,
      Provider<CreateDirectoryTool> createDirectoryToolProvider,
      Provider<DeleteFileTool> deleteFileToolProvider, Provider<RunShellTool> runShellToolProvider,
      Provider<CopyFileTool> copyFileToolProvider, Provider<MoveFileTool> moveFileToolProvider,
      Provider<SearchFilesTool> searchFilesToolProvider,
      Provider<EditFileTool> editFileToolProvider,
      Provider<GetFileInfoTool> getFileInfoToolProvider,
      Provider<FindFilesTool> findFilesToolProvider,
      Provider<UndoChangeTool> undoChangeToolProvider,
      Provider<BatchReadTool> batchReadToolProvider, Provider<ApplyDiffTool> applyDiffToolProvider,
      Provider<CreateReminderTool> createReminderToolProvider,
      Provider<UpdateMemoryTool> updateMemoryToolProvider,
      Provider<UpdateTodoTool> updateTodoToolProvider,
      Provider<InvokeSubagentsTool> invokeSubagentsToolProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    this.listFilesToolProvider = listFilesToolProvider;
    this.readFileToolProvider = readFileToolProvider;
    this.writeFileToolProvider = writeFileToolProvider;
    this.createDirectoryToolProvider = createDirectoryToolProvider;
    this.deleteFileToolProvider = deleteFileToolProvider;
    this.runShellToolProvider = runShellToolProvider;
    this.copyFileToolProvider = copyFileToolProvider;
    this.moveFileToolProvider = moveFileToolProvider;
    this.searchFilesToolProvider = searchFilesToolProvider;
    this.editFileToolProvider = editFileToolProvider;
    this.getFileInfoToolProvider = getFileInfoToolProvider;
    this.findFilesToolProvider = findFilesToolProvider;
    this.undoChangeToolProvider = undoChangeToolProvider;
    this.batchReadToolProvider = batchReadToolProvider;
    this.applyDiffToolProvider = applyDiffToolProvider;
    this.createReminderToolProvider = createReminderToolProvider;
    this.updateMemoryToolProvider = updateMemoryToolProvider;
    this.updateTodoToolProvider = updateTodoToolProvider;
    this.invokeSubagentsToolProvider = invokeSubagentsToolProvider;
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public ToolExecutor get() {
    return newInstance(listFilesToolProvider.get(), readFileToolProvider.get(), writeFileToolProvider.get(), createDirectoryToolProvider.get(), deleteFileToolProvider.get(), runShellToolProvider.get(), copyFileToolProvider.get(), moveFileToolProvider.get(), searchFilesToolProvider.get(), editFileToolProvider.get(), getFileInfoToolProvider.get(), findFilesToolProvider.get(), undoChangeToolProvider.get(), batchReadToolProvider.get(), applyDiffToolProvider.get(), createReminderToolProvider.get(), updateMemoryToolProvider.get(), updateTodoToolProvider.get(), invokeSubagentsToolProvider.get(), commandValidatorProvider.get());
  }

  public static ToolExecutor_Factory create(Provider<ListFilesTool> listFilesToolProvider,
      Provider<ReadFileTool> readFileToolProvider, Provider<WriteFileTool> writeFileToolProvider,
      Provider<CreateDirectoryTool> createDirectoryToolProvider,
      Provider<DeleteFileTool> deleteFileToolProvider, Provider<RunShellTool> runShellToolProvider,
      Provider<CopyFileTool> copyFileToolProvider, Provider<MoveFileTool> moveFileToolProvider,
      Provider<SearchFilesTool> searchFilesToolProvider,
      Provider<EditFileTool> editFileToolProvider,
      Provider<GetFileInfoTool> getFileInfoToolProvider,
      Provider<FindFilesTool> findFilesToolProvider,
      Provider<UndoChangeTool> undoChangeToolProvider,
      Provider<BatchReadTool> batchReadToolProvider, Provider<ApplyDiffTool> applyDiffToolProvider,
      Provider<CreateReminderTool> createReminderToolProvider,
      Provider<UpdateMemoryTool> updateMemoryToolProvider,
      Provider<UpdateTodoTool> updateTodoToolProvider,
      Provider<InvokeSubagentsTool> invokeSubagentsToolProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    return new ToolExecutor_Factory(listFilesToolProvider, readFileToolProvider, writeFileToolProvider, createDirectoryToolProvider, deleteFileToolProvider, runShellToolProvider, copyFileToolProvider, moveFileToolProvider, searchFilesToolProvider, editFileToolProvider, getFileInfoToolProvider, findFilesToolProvider, undoChangeToolProvider, batchReadToolProvider, applyDiffToolProvider, createReminderToolProvider, updateMemoryToolProvider, updateTodoToolProvider, invokeSubagentsToolProvider, commandValidatorProvider);
  }

  public static ToolExecutor newInstance(ListFilesTool listFilesTool, ReadFileTool readFileTool,
      WriteFileTool writeFileTool, CreateDirectoryTool createDirectoryTool,
      DeleteFileTool deleteFileTool, RunShellTool runShellTool, CopyFileTool copyFileTool,
      MoveFileTool moveFileTool, SearchFilesTool searchFilesTool, EditFileTool editFileTool,
      GetFileInfoTool getFileInfoTool, FindFilesTool findFilesTool, UndoChangeTool undoChangeTool,
      BatchReadTool batchReadTool, ApplyDiffTool applyDiffTool,
      CreateReminderTool createReminderTool, UpdateMemoryTool updateMemoryTool,
      UpdateTodoTool updateTodoTool, InvokeSubagentsTool invokeSubagentsTool,
      CommandValidator commandValidator) {
    return new ToolExecutor(listFilesTool, readFileTool, writeFileTool, createDirectoryTool, deleteFileTool, runShellTool, copyFileTool, moveFileTool, searchFilesTool, editFileTool, getFileInfoTool, findFilesTool, undoChangeTool, batchReadTool, applyDiffTool, createReminderTool, updateMemoryTool, updateTodoTool, invokeSubagentsTool, commandValidator);
  }
}
