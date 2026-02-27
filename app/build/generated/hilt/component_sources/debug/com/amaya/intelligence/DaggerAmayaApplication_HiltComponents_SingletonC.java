package com.amaya.intelligence;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.amaya.intelligence.data.local.db.AppDatabase;
import com.amaya.intelligence.data.local.db.dao.ConversationDao;
import com.amaya.intelligence.data.local.db.dao.CronJobDao;
import com.amaya.intelligence.data.local.db.dao.FileDao;
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao;
import com.amaya.intelligence.data.local.db.dao.ProjectDao;
import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.remote.api.AnthropicProvider;
import com.amaya.intelligence.data.remote.api.GeminiProvider;
import com.amaya.intelligence.data.remote.api.OpenAiProvider;
import com.amaya.intelligence.data.remote.mcp.McpClientManager;
import com.amaya.intelligence.data.remote.mcp.McpToolExecutor;
import com.amaya.intelligence.data.repository.AiRepository;
import com.amaya.intelligence.data.repository.CronJobRepository;
import com.amaya.intelligence.data.repository.FileIndexRepository;
import com.amaya.intelligence.data.repository.PersonaRepository;
import com.amaya.intelligence.di.AiModule_ProvideAnthropicProviderFactory;
import com.amaya.intelligence.di.AiModule_ProvideGeminiProviderFactory;
import com.amaya.intelligence.di.AiModule_ProvideOpenAiProviderFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideAppDatabaseFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideConversationDaoFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideCronJobDaoFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideFileDaoFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideFileMetadataDaoFactory;
import com.amaya.intelligence.di.DatabaseModule_ProvideProjectDaoFactory;
import com.amaya.intelligence.di.NetworkModule_ProvideMoshiFactory;
import com.amaya.intelligence.di.NetworkModule_ProvideOkHttpClientFactory;
import com.amaya.intelligence.domain.security.CommandValidator;
import com.amaya.intelligence.service.BootReceiver;
import com.amaya.intelligence.service.BootReceiver_MembersInjector;
import com.amaya.intelligence.service.ReminderWorker;
import com.amaya.intelligence.service.ReminderWorker_AssistedFactory;
import com.amaya.intelligence.tools.CreateDirectoryTool;
import com.amaya.intelligence.tools.CreateReminderTool;
import com.amaya.intelligence.tools.DeleteFileTool;
import com.amaya.intelligence.tools.EditFileTool;
import com.amaya.intelligence.tools.FindFilesTool;
import com.amaya.intelligence.tools.InvokeSubagentsTool;
import com.amaya.intelligence.tools.ListFilesTool;
import com.amaya.intelligence.tools.ReadFileTool;
import com.amaya.intelligence.tools.RunShellTool;
import com.amaya.intelligence.tools.SubagentRunner;
import com.amaya.intelligence.tools.TodoRepository;
import com.amaya.intelligence.tools.ToolExecutor;
import com.amaya.intelligence.tools.TransferFileTool;
import com.amaya.intelligence.tools.UndoChangeTool;
import com.amaya.intelligence.tools.UpdateMemoryTool;
import com.amaya.intelligence.tools.UpdateTodoTool;
import com.amaya.intelligence.tools.WriteFileTool;
import com.amaya.intelligence.ui.AppViewModel;
import com.amaya.intelligence.ui.AppViewModel_HiltModules;
import com.amaya.intelligence.ui.MainActivity;
import com.amaya.intelligence.ui.MainActivity_MembersInjector;
import com.amaya.intelligence.ui.chat.ChatViewModel;
import com.amaya.intelligence.ui.chat.ChatViewModel_HiltModules;
import com.amaya.intelligence.ui.settings.AgentsActivity;
import com.amaya.intelligence.ui.settings.AgentsActivity_MembersInjector;
import com.amaya.intelligence.ui.settings.CronJobActivity;
import com.amaya.intelligence.ui.settings.CronJobActivity_MembersInjector;
import com.amaya.intelligence.ui.settings.McpActivity;
import com.amaya.intelligence.ui.settings.McpActivity_MembersInjector;
import com.amaya.intelligence.ui.settings.PersonaActivity;
import com.amaya.intelligence.ui.settings.PersonaActivity_MembersInjector;
import com.amaya.intelligence.ui.settings.SettingsActivity;
import com.amaya.intelligence.ui.settings.SettingsActivity_MembersInjector;
import com.amaya.intelligence.ui.workspace.WorkspaceActivity;
import com.amaya.intelligence.ui.workspace.WorkspaceActivity_MembersInjector;
import com.squareup.moshi.Moshi;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DelegateFactory;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

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
public final class DaggerAmayaApplication_HiltComponents_SingletonC {
  private DaggerAmayaApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public AmayaApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements AmayaApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements AmayaApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements AmayaApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements AmayaApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements AmayaApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements AmayaApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements AmayaApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public AmayaApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends AmayaApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends AmayaApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends AmayaApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends AmayaApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public void injectAgentsActivity(AgentsActivity agentsActivity) {
      injectAgentsActivity2(agentsActivity);
    }

    @Override
    public void injectCronJobActivity(CronJobActivity cronJobActivity) {
      injectCronJobActivity2(cronJobActivity);
    }

    @Override
    public void injectMcpActivity(McpActivity mcpActivity) {
      injectMcpActivity2(mcpActivity);
    }

    @Override
    public void injectPersonaActivity(PersonaActivity personaActivity) {
      injectPersonaActivity2(personaActivity);
    }

    @Override
    public void injectSettingsActivity(SettingsActivity settingsActivity) {
      injectSettingsActivity2(settingsActivity);
    }

    @Override
    public void injectWorkspaceActivity(WorkspaceActivity workspaceActivity) {
      injectWorkspaceActivity2(workspaceActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(2).put(LazyClassKeyProvider.com_amaya_intelligence_ui_AppViewModel, AppViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_amaya_intelligence_ui_chat_ChatViewModel, ChatViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectAiSettingsManager(instance, singletonCImpl.aiSettingsManagerProvider.get());
      return instance;
    }

    private AgentsActivity injectAgentsActivity2(AgentsActivity instance2) {
      AgentsActivity_MembersInjector.injectAiSettingsManager(instance2, singletonCImpl.aiSettingsManagerProvider.get());
      return instance2;
    }

    private CronJobActivity injectCronJobActivity2(CronJobActivity instance3) {
      CronJobActivity_MembersInjector.injectCronJobRepository(instance3, singletonCImpl.cronJobRepositoryProvider.get());
      return instance3;
    }

    private McpActivity injectMcpActivity2(McpActivity instance4) {
      McpActivity_MembersInjector.injectAiSettingsManager(instance4, singletonCImpl.aiSettingsManagerProvider.get());
      return instance4;
    }

    private PersonaActivity injectPersonaActivity2(PersonaActivity instance5) {
      PersonaActivity_MembersInjector.injectPersonaRepository(instance5, singletonCImpl.personaRepositoryProvider.get());
      PersonaActivity_MembersInjector.injectAiSettingsManager(instance5, singletonCImpl.aiSettingsManagerProvider.get());
      return instance5;
    }

    private SettingsActivity injectSettingsActivity2(SettingsActivity instance6) {
      SettingsActivity_MembersInjector.injectAiSettingsManager(instance6, singletonCImpl.aiSettingsManagerProvider.get());
      return instance6;
    }

    private WorkspaceActivity injectWorkspaceActivity2(WorkspaceActivity instance7) {
      WorkspaceActivity_MembersInjector.injectAiSettingsManager(instance7, singletonCImpl.aiSettingsManagerProvider.get());
      return instance7;
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_amaya_intelligence_ui_AppViewModel = "com.amaya.intelligence.ui.AppViewModel";

      static String com_amaya_intelligence_ui_chat_ChatViewModel = "com.amaya.intelligence.ui.chat.ChatViewModel";

      @KeepFieldType
      AppViewModel com_amaya_intelligence_ui_AppViewModel2;

      @KeepFieldType
      ChatViewModel com_amaya_intelligence_ui_chat_ChatViewModel2;
    }
  }

  private static final class ViewModelCImpl extends AmayaApplication_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AppViewModel> appViewModelProvider;

    private Provider<ChatViewModel> chatViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.appViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.chatViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(2).put(LazyClassKeyProvider.com_amaya_intelligence_ui_AppViewModel, ((Provider) appViewModelProvider)).put(LazyClassKeyProvider.com_amaya_intelligence_ui_chat_ChatViewModel, ((Provider) chatViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_amaya_intelligence_ui_AppViewModel = "com.amaya.intelligence.ui.AppViewModel";

      static String com_amaya_intelligence_ui_chat_ChatViewModel = "com.amaya.intelligence.ui.chat.ChatViewModel";

      @KeepFieldType
      AppViewModel com_amaya_intelligence_ui_AppViewModel2;

      @KeepFieldType
      ChatViewModel com_amaya_intelligence_ui_chat_ChatViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.amaya.intelligence.ui.AppViewModel 
          return (T) new AppViewModel(singletonCImpl.cronJobRepositoryProvider.get());

          case 1: // com.amaya.intelligence.ui.chat.ChatViewModel 
          return (T) new ChatViewModel(singletonCImpl.aiRepositoryProvider.get(), singletonCImpl.provideConversationDaoProvider.get(), singletonCImpl.aiSettingsManagerProvider.get(), singletonCImpl.cronJobRepositoryProvider.get(), singletonCImpl.personaRepositoryProvider.get(), singletonCImpl.todoRepositoryProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends AmayaApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends AmayaApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends AmayaApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<AppDatabase> provideAppDatabaseProvider;

    private Provider<ConversationDao> provideConversationDaoProvider;

    private Provider<CronJobDao> provideCronJobDaoProvider;

    private Provider<CronJobRepository> cronJobRepositoryProvider;

    private Provider<OkHttpClient> provideOkHttpClientProvider;

    private Provider<Moshi> provideMoshiProvider;

    private Provider<AiSettingsManager> aiSettingsManagerProvider;

    private Provider<AnthropicProvider> provideAnthropicProvider;

    private Provider<OpenAiProvider> provideOpenAiProvider;

    private Provider<GeminiProvider> provideGeminiProvider;

    private Provider<CommandValidator> commandValidatorProvider;

    private Provider<ListFilesTool> listFilesToolProvider;

    private Provider<ReadFileTool> readFileToolProvider;

    private Provider<WriteFileTool> writeFileToolProvider;

    private Provider<CreateDirectoryTool> createDirectoryToolProvider;

    private Provider<DeleteFileTool> deleteFileToolProvider;

    private Provider<RunShellTool> runShellToolProvider;

    private Provider<TransferFileTool> transferFileToolProvider;

    private Provider<EditFileTool> editFileToolProvider;

    private Provider<FindFilesTool> findFilesToolProvider;

    private Provider<UndoChangeTool> undoChangeToolProvider;

    private Provider<CreateReminderTool> createReminderToolProvider;

    private Provider<PersonaRepository> personaRepositoryProvider;

    private Provider<UpdateMemoryTool> updateMemoryToolProvider;

    private Provider<TodoRepository> todoRepositoryProvider;

    private Provider<UpdateTodoTool> updateTodoToolProvider;

    private Provider<ToolExecutor> toolExecutorProvider;

    private Provider<SubagentRunner> subagentRunnerProvider;

    private Provider<InvokeSubagentsTool> invokeSubagentsToolProvider;

    private Provider<McpClientManager> mcpClientManagerProvider;

    private Provider<McpToolExecutor> mcpToolExecutorProvider;

    private Provider<ProjectDao> provideProjectDaoProvider;

    private Provider<FileDao> provideFileDaoProvider;

    private Provider<FileMetadataDao> provideFileMetadataDaoProvider;

    private Provider<FileIndexRepository> fileIndexRepositoryProvider;

    private Provider<AiRepository> aiRepositoryProvider;

    private Provider<ReminderWorker_AssistedFactory> reminderWorker_AssistedFactoryProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);
      initialize2(applicationContextModuleParam);

    }

    private Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return Collections.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>singletonMap("com.amaya.intelligence.service.ReminderWorker", ((Provider) reminderWorker_AssistedFactoryProvider));
    }

    private HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideAppDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 2));
      this.provideConversationDaoProvider = DoubleCheck.provider(new SwitchingProvider<ConversationDao>(singletonCImpl, 1));
      this.provideCronJobDaoProvider = DoubleCheck.provider(new SwitchingProvider<CronJobDao>(singletonCImpl, 4));
      this.cronJobRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<CronJobRepository>(singletonCImpl, 3));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 7));
      this.provideMoshiProvider = DoubleCheck.provider(new SwitchingProvider<Moshi>(singletonCImpl, 8));
      this.aiSettingsManagerProvider = DoubleCheck.provider(new SwitchingProvider<AiSettingsManager>(singletonCImpl, 9));
      this.provideAnthropicProvider = DoubleCheck.provider(new SwitchingProvider<AnthropicProvider>(singletonCImpl, 6));
      this.provideOpenAiProvider = DoubleCheck.provider(new SwitchingProvider<OpenAiProvider>(singletonCImpl, 10));
      this.provideGeminiProvider = DoubleCheck.provider(new SwitchingProvider<GeminiProvider>(singletonCImpl, 11));
      this.commandValidatorProvider = DoubleCheck.provider(new SwitchingProvider<CommandValidator>(singletonCImpl, 14));
      this.listFilesToolProvider = DoubleCheck.provider(new SwitchingProvider<ListFilesTool>(singletonCImpl, 13));
      this.readFileToolProvider = DoubleCheck.provider(new SwitchingProvider<ReadFileTool>(singletonCImpl, 15));
      this.writeFileToolProvider = DoubleCheck.provider(new SwitchingProvider<WriteFileTool>(singletonCImpl, 16));
      this.createDirectoryToolProvider = DoubleCheck.provider(new SwitchingProvider<CreateDirectoryTool>(singletonCImpl, 17));
      this.deleteFileToolProvider = DoubleCheck.provider(new SwitchingProvider<DeleteFileTool>(singletonCImpl, 18));
      this.runShellToolProvider = DoubleCheck.provider(new SwitchingProvider<RunShellTool>(singletonCImpl, 19));
      this.transferFileToolProvider = DoubleCheck.provider(new SwitchingProvider<TransferFileTool>(singletonCImpl, 20));
      this.editFileToolProvider = DoubleCheck.provider(new SwitchingProvider<EditFileTool>(singletonCImpl, 21));
      this.findFilesToolProvider = DoubleCheck.provider(new SwitchingProvider<FindFilesTool>(singletonCImpl, 22));
      this.undoChangeToolProvider = DoubleCheck.provider(new SwitchingProvider<UndoChangeTool>(singletonCImpl, 23));
      this.createReminderToolProvider = DoubleCheck.provider(new SwitchingProvider<CreateReminderTool>(singletonCImpl, 24));
      this.personaRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<PersonaRepository>(singletonCImpl, 26));
      this.updateMemoryToolProvider = DoubleCheck.provider(new SwitchingProvider<UpdateMemoryTool>(singletonCImpl, 25));
      this.todoRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<TodoRepository>(singletonCImpl, 28));
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final ApplicationContextModule applicationContextModuleParam) {
      this.updateTodoToolProvider = DoubleCheck.provider(new SwitchingProvider<UpdateTodoTool>(singletonCImpl, 27));
      this.toolExecutorProvider = new DelegateFactory<>();
      this.subagentRunnerProvider = DoubleCheck.provider(new SwitchingProvider<SubagentRunner>(singletonCImpl, 30));
      this.invokeSubagentsToolProvider = DoubleCheck.provider(new SwitchingProvider<InvokeSubagentsTool>(singletonCImpl, 29));
      DelegateFactory.setDelegate(toolExecutorProvider, DoubleCheck.provider(new SwitchingProvider<ToolExecutor>(singletonCImpl, 12)));
      this.mcpClientManagerProvider = DoubleCheck.provider(new SwitchingProvider<McpClientManager>(singletonCImpl, 32));
      this.mcpToolExecutorProvider = DoubleCheck.provider(new SwitchingProvider<McpToolExecutor>(singletonCImpl, 31));
      this.provideProjectDaoProvider = DoubleCheck.provider(new SwitchingProvider<ProjectDao>(singletonCImpl, 34));
      this.provideFileDaoProvider = DoubleCheck.provider(new SwitchingProvider<FileDao>(singletonCImpl, 35));
      this.provideFileMetadataDaoProvider = DoubleCheck.provider(new SwitchingProvider<FileMetadataDao>(singletonCImpl, 36));
      this.fileIndexRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<FileIndexRepository>(singletonCImpl, 33));
      this.aiRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<AiRepository>(singletonCImpl, 5));
      this.reminderWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<ReminderWorker_AssistedFactory>(singletonCImpl, 0));
    }

    @Override
    public void injectAmayaApplication(AmayaApplication amayaApplication) {
      injectAmayaApplication2(amayaApplication);
    }

    @Override
    public void injectBootReceiver(BootReceiver bootReceiver) {
      injectBootReceiver2(bootReceiver);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private AmayaApplication injectAmayaApplication2(AmayaApplication instance) {
      AmayaApplication_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      return instance;
    }

    private BootReceiver injectBootReceiver2(BootReceiver instance2) {
      BootReceiver_MembersInjector.injectCronJobRepository(instance2, cronJobRepositoryProvider.get());
      return instance2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.amaya.intelligence.service.ReminderWorker_AssistedFactory 
          return (T) new ReminderWorker_AssistedFactory() {
            @Override
            public ReminderWorker create(Context context, WorkerParameters params) {
              return new ReminderWorker(context, params, singletonCImpl.provideConversationDaoProvider.get(), singletonCImpl.cronJobRepositoryProvider.get(), singletonCImpl.aiRepositoryProvider.get(), singletonCImpl.aiSettingsManagerProvider.get());
            }
          };

          case 1: // com.amaya.intelligence.data.local.db.dao.ConversationDao 
          return (T) DatabaseModule_ProvideConversationDaoFactory.provideConversationDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 2: // com.amaya.intelligence.data.local.db.AppDatabase 
          return (T) DatabaseModule_ProvideAppDatabaseFactory.provideAppDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.amaya.intelligence.data.repository.CronJobRepository 
          return (T) new CronJobRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideCronJobDaoProvider.get());

          case 4: // com.amaya.intelligence.data.local.db.dao.CronJobDao 
          return (T) DatabaseModule_ProvideCronJobDaoFactory.provideCronJobDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 5: // com.amaya.intelligence.data.repository.AiRepository 
          return (T) new AiRepository(singletonCImpl.provideAnthropicProvider.get(), singletonCImpl.provideOpenAiProvider.get(), singletonCImpl.provideGeminiProvider.get(), singletonCImpl.aiSettingsManagerProvider.get(), singletonCImpl.toolExecutorProvider.get(), singletonCImpl.mcpToolExecutorProvider.get(), singletonCImpl.fileIndexRepositoryProvider.get(), singletonCImpl.personaRepositoryProvider.get(), singletonCImpl.mcpClientManagerProvider.get());

          case 6: // com.amaya.intelligence.data.remote.api.AnthropicProvider 
          return (T) AiModule_ProvideAnthropicProviderFactory.provideAnthropicProvider(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get(), singletonCImpl.aiSettingsManagerProvider.get());

          case 7: // okhttp3.OkHttpClient 
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient();

          case 8: // com.squareup.moshi.Moshi 
          return (T) NetworkModule_ProvideMoshiFactory.provideMoshi();

          case 9: // com.amaya.intelligence.data.remote.api.AiSettingsManager 
          return (T) new AiSettingsManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.amaya.intelligence.data.remote.api.OpenAiProvider 
          return (T) AiModule_ProvideOpenAiProviderFactory.provideOpenAiProvider(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get(), singletonCImpl.aiSettingsManagerProvider.get());

          case 11: // com.amaya.intelligence.data.remote.api.GeminiProvider 
          return (T) AiModule_ProvideGeminiProviderFactory.provideGeminiProvider(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get(), singletonCImpl.aiSettingsManagerProvider.get());

          case 12: // com.amaya.intelligence.tools.ToolExecutor 
          return (T) new ToolExecutor(singletonCImpl.listFilesToolProvider.get(), singletonCImpl.readFileToolProvider.get(), singletonCImpl.writeFileToolProvider.get(), singletonCImpl.createDirectoryToolProvider.get(), singletonCImpl.deleteFileToolProvider.get(), singletonCImpl.runShellToolProvider.get(), singletonCImpl.transferFileToolProvider.get(), singletonCImpl.editFileToolProvider.get(), singletonCImpl.findFilesToolProvider.get(), singletonCImpl.undoChangeToolProvider.get(), singletonCImpl.createReminderToolProvider.get(), singletonCImpl.updateMemoryToolProvider.get(), singletonCImpl.updateTodoToolProvider.get(), singletonCImpl.invokeSubagentsToolProvider.get(), singletonCImpl.commandValidatorProvider.get());

          case 13: // com.amaya.intelligence.tools.ListFilesTool 
          return (T) new ListFilesTool(singletonCImpl.commandValidatorProvider.get());

          case 14: // com.amaya.intelligence.domain.security.CommandValidator 
          return (T) new CommandValidator(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.amaya.intelligence.tools.ReadFileTool 
          return (T) new ReadFileTool(singletonCImpl.commandValidatorProvider.get());

          case 16: // com.amaya.intelligence.tools.WriteFileTool 
          return (T) new WriteFileTool(singletonCImpl.commandValidatorProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 17: // com.amaya.intelligence.tools.CreateDirectoryTool 
          return (T) new CreateDirectoryTool(singletonCImpl.commandValidatorProvider.get());

          case 18: // com.amaya.intelligence.tools.DeleteFileTool 
          return (T) new DeleteFileTool(singletonCImpl.commandValidatorProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 19: // com.amaya.intelligence.tools.RunShellTool 
          return (T) new RunShellTool(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.commandValidatorProvider.get());

          case 20: // com.amaya.intelligence.tools.TransferFileTool 
          return (T) new TransferFileTool(singletonCImpl.commandValidatorProvider.get());

          case 21: // com.amaya.intelligence.tools.EditFileTool 
          return (T) new EditFileTool(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.commandValidatorProvider.get());

          case 22: // com.amaya.intelligence.tools.FindFilesTool 
          return (T) new FindFilesTool(singletonCImpl.commandValidatorProvider.get());

          case 23: // com.amaya.intelligence.tools.UndoChangeTool 
          return (T) new UndoChangeTool(singletonCImpl.commandValidatorProvider.get());

          case 24: // com.amaya.intelligence.tools.CreateReminderTool 
          return (T) new CreateReminderTool(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.cronJobRepositoryProvider.get());

          case 25: // com.amaya.intelligence.tools.UpdateMemoryTool 
          return (T) new UpdateMemoryTool(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.personaRepositoryProvider.get());

          case 26: // com.amaya.intelligence.data.repository.PersonaRepository 
          return (T) new PersonaRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 27: // com.amaya.intelligence.tools.UpdateTodoTool 
          return (T) new UpdateTodoTool(singletonCImpl.todoRepositoryProvider.get());

          case 28: // com.amaya.intelligence.tools.TodoRepository 
          return (T) new TodoRepository();

          case 29: // com.amaya.intelligence.tools.InvokeSubagentsTool 
          return (T) new InvokeSubagentsTool(singletonCImpl.subagentRunnerProvider.get());

          case 30: // com.amaya.intelligence.tools.SubagentRunner 
          return (T) new SubagentRunner(singletonCImpl.provideAnthropicProvider.get(), singletonCImpl.provideOpenAiProvider.get(), singletonCImpl.provideGeminiProvider.get(), singletonCImpl.aiSettingsManagerProvider.get(), singletonCImpl.toolExecutorProvider);

          case 31: // com.amaya.intelligence.data.remote.mcp.McpToolExecutor 
          return (T) new McpToolExecutor(singletonCImpl.toolExecutorProvider.get(), singletonCImpl.mcpClientManagerProvider.get());

          case 32: // com.amaya.intelligence.data.remote.mcp.McpClientManager 
          return (T) new McpClientManager(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.aiSettingsManagerProvider.get());

          case 33: // com.amaya.intelligence.data.repository.FileIndexRepository 
          return (T) new FileIndexRepository(singletonCImpl.provideProjectDaoProvider.get(), singletonCImpl.provideFileDaoProvider.get(), singletonCImpl.provideFileMetadataDaoProvider.get());

          case 34: // com.amaya.intelligence.data.local.db.dao.ProjectDao 
          return (T) DatabaseModule_ProvideProjectDaoFactory.provideProjectDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 35: // com.amaya.intelligence.data.local.db.dao.FileDao 
          return (T) DatabaseModule_ProvideFileDaoFactory.provideFileDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 36: // com.amaya.intelligence.data.local.db.dao.FileMetadataDao 
          return (T) DatabaseModule_ProvideFileMetadataDaoFactory.provideFileMetadataDao(singletonCImpl.provideAppDatabaseProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
