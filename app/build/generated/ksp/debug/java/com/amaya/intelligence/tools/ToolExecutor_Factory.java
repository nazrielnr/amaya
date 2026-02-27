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

  private final Provider<TransferFileTool> transferFileToolProvider;

  private final Provider<EditFileTool> editFileToolProvider;

  private final Provider<FindFilesTool> findFilesToolProvider;

  private final Provider<UndoChangeTool> undoChangeToolProvider;

  private final Provider<CreateReminderTool> createReminderToolProvider;

  private final Provider<UpdateMemoryTool> updateMemoryToolProvider;

  private final Provider<UpdateTodoTool> updateTodoToolProvider;

  private final Provider<InvokeSubagentsTool> invokeSubagentsToolProvider;

  private final Provider<CommandValidator> commandValidatorProvider;

  public ToolExecutor_Factory(Provider<ListFilesTool> listFilesToolProvider,
      Provider<ReadFileTool> readFileToolProvider, Provider<WriteFileTool> writeFileToolProvider,
      Provider<CreateDirectoryTool> createDirectoryToolProvider,
      Provider<DeleteFileTool> deleteFileToolProvider, Provider<RunShellTool> runShellToolProvider,
      Provider<TransferFileTool> transferFileToolProvider,
      Provider<EditFileTool> editFileToolProvider, Provider<FindFilesTool> findFilesToolProvider,
      Provider<UndoChangeTool> undoChangeToolProvider,
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
    this.transferFileToolProvider = transferFileToolProvider;
    this.editFileToolProvider = editFileToolProvider;
    this.findFilesToolProvider = findFilesToolProvider;
    this.undoChangeToolProvider = undoChangeToolProvider;
    this.createReminderToolProvider = createReminderToolProvider;
    this.updateMemoryToolProvider = updateMemoryToolProvider;
    this.updateTodoToolProvider = updateTodoToolProvider;
    this.invokeSubagentsToolProvider = invokeSubagentsToolProvider;
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public ToolExecutor get() {
    return newInstance(listFilesToolProvider.get(), readFileToolProvider.get(), writeFileToolProvider.get(), createDirectoryToolProvider.get(), deleteFileToolProvider.get(), runShellToolProvider.get(), transferFileToolProvider.get(), editFileToolProvider.get(), findFilesToolProvider.get(), undoChangeToolProvider.get(), createReminderToolProvider.get(), updateMemoryToolProvider.get(), updateTodoToolProvider.get(), invokeSubagentsToolProvider.get(), commandValidatorProvider.get());
  }

  public static ToolExecutor_Factory create(Provider<ListFilesTool> listFilesToolProvider,
      Provider<ReadFileTool> readFileToolProvider, Provider<WriteFileTool> writeFileToolProvider,
      Provider<CreateDirectoryTool> createDirectoryToolProvider,
      Provider<DeleteFileTool> deleteFileToolProvider, Provider<RunShellTool> runShellToolProvider,
      Provider<TransferFileTool> transferFileToolProvider,
      Provider<EditFileTool> editFileToolProvider, Provider<FindFilesTool> findFilesToolProvider,
      Provider<UndoChangeTool> undoChangeToolProvider,
      Provider<CreateReminderTool> createReminderToolProvider,
      Provider<UpdateMemoryTool> updateMemoryToolProvider,
      Provider<UpdateTodoTool> updateTodoToolProvider,
      Provider<InvokeSubagentsTool> invokeSubagentsToolProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    return new ToolExecutor_Factory(listFilesToolProvider, readFileToolProvider, writeFileToolProvider, createDirectoryToolProvider, deleteFileToolProvider, runShellToolProvider, transferFileToolProvider, editFileToolProvider, findFilesToolProvider, undoChangeToolProvider, createReminderToolProvider, updateMemoryToolProvider, updateTodoToolProvider, invokeSubagentsToolProvider, commandValidatorProvider);
  }

  public static ToolExecutor newInstance(ListFilesTool listFilesTool, ReadFileTool readFileTool,
      WriteFileTool writeFileTool, CreateDirectoryTool createDirectoryTool,
      DeleteFileTool deleteFileTool, RunShellTool runShellTool, TransferFileTool transferFileTool,
      EditFileTool editFileTool, FindFilesTool findFilesTool, UndoChangeTool undoChangeTool,
      CreateReminderTool createReminderTool, UpdateMemoryTool updateMemoryTool,
      UpdateTodoTool updateTodoTool, InvokeSubagentsTool invokeSubagentsTool,
      CommandValidator commandValidator) {
    return new ToolExecutor(listFilesTool, readFileTool, writeFileTool, createDirectoryTool, deleteFileTool, runShellTool, transferFileTool, editFileTool, findFilesTool, undoChangeTool, createReminderTool, updateMemoryTool, updateTodoTool, invokeSubagentsTool, commandValidator);
  }
}
