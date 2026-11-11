package com.amaya.intelligence.ui.screens.settings.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.local.entity.AgentEntity
import com.amaya.intelligence.data.local.entity.AgentGroupEntity
import com.amaya.intelligence.data.local.entity.ProjectEntity
import com.amaya.intelligence.data.remote.api.AiSettings
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.domain.models.UpdateInfo
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonAboveTabBarPadding
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput

enum class SettingsScope(@androidx.annotation.StringRes val label: Int) {
    GLOBAL(R.string.global), PROJECT(R.string.project), AGENT(R.string.agent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSettingsScreen(
    onNavigateBack: () -> Unit,
    initialScope: SettingsScope,
    projects: List<ProjectEntity>,
    agentGroups: List<AgentGroupEntity>,
    agents: List<AgentEntity>,
    aiSettingsManager: AiSettingsManager,
    onNavigateToModels: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToAboutYou: () -> Unit,
    onNavigateToSkills: () -> Unit,
    updateStatus: String?,
    isDownloadingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit,
    onCreateProject: (String, String, String) -> Unit,
    onSelectProjectWorkspace: () -> Unit,
    selectedProjectWorkspace: String?,
    onOpenAgentGroup: (AgentGroupEntity) -> Unit,
    onCreateAgentGroup: (String, String, String) -> Unit,
    onSelectAgentWorkspace: () -> Unit,
    selectedAgentWorkspace: String?
) {
    val pagerState = rememberPagerState(
        initialPage = SettingsScope.entries.indexOf(initialScope),
        pageCount = { SettingsScope.entries.size }
    )
    val selectedScope = SettingsScope.entries[pagerState.targetPage]
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(initial = AiSettings())
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    var addSheet by remember { mutableStateOf<SettingsScope?>(null) }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0.dp), snackbarHost = { SnackbarHost(snackbar) }) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize().background(colors.groupedBackground)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageScope = SettingsScope.entries[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AmayaGroupedSettingsTokens.contentHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(AmayaGroupedSettingsTokens.sectionSpacing)
                ) {
                    Spacer(
                        Modifier
                            .statusBarsPadding()
                            .height(AmayaGroupedSettingsTokens.screenContentTopSpacer)
                    )

                    when (pageScope) {
                        SettingsScope.GLOBAL -> GlobalSettings(
                            settings = settings,
                            onModels = onNavigateToModels,
                            onAboutYou = onNavigateToAboutYou,
                            onMcp = onNavigateToMcp,
                            onSkills = onNavigateToSkills,
                            onTerminal = onNavigateToTerminal,
                            updateStatus = updateStatus,
                            isDownloadingUpdate = isDownloadingUpdate,
                            updateInfo = updateInfo,
                            onCheckForUpdate = onCheckForUpdate,
                            onInstallUpdate = onInstallUpdate,
                            onSelectTheme = { theme -> scope.launch { aiSettingsManager.setTheme(theme) } },
                            onSnackbar = { text -> scope.launch { snackbar.showSnackbar(text) } }
                        )
                        SettingsScope.PROJECT -> ProjectListSettings(projects, onOpenProject)
                        SettingsScope.AGENT -> AgentListSettings(agentGroups, agents, onOpenAgentGroup)
                    }

                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(AmayaGroupedSettingsTokens.bottomTabBarContentClearance)
                    )
                }
            }

            com.amaya.intelligence.ui.screens.amaya.AmayaTopScrim(
                Modifier.align(Alignment.TopCenter)
            )

            Box(Modifier.align(Alignment.BottomCenter)) {
                FloatingPillTabBar(
                    pagerState = pagerState
                )
            }

            if (selectedScope != SettingsScope.GLOBAL) {
                ExtendedFloatingActionButton(
                    onClick = { addSheet = selectedScope },
                    icon = { Icon(Icons.Default.Add, if (selectedScope == SettingsScope.PROJECT) stringResource(R.string.add_project) else stringResource(R.string.add_agent_group)) },
                    text = { Text(if (selectedScope == SettingsScope.PROJECT) stringResource(R.string.project) else stringResource(R.string.agent)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                        .amayaFloatingActionButtonAboveTabBarPadding()
                )
            }
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp)) },
                navigationIcon = { SettingsBackButton(onClick = onNavigateBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp),
                windowInsets = WindowInsets(0.dp)
            )
        }
    }

    when (addSheet) {
        SettingsScope.PROJECT -> AddOwnerSheet(
            title = stringResource(R.string.new_project),
            nameLabel = stringResource(R.string.project_name),
            instructionsLabel = stringResource(R.string.project_instructions),
            workspace = selectedProjectWorkspace,
            onSelectWorkspace = onSelectProjectWorkspace,
            onCreate = { name, instructions, workspace -> onCreateProject(name, instructions, workspace); addSheet = null },
            onDismiss = { addSheet = null }
        )
        SettingsScope.AGENT -> AddOwnerSheet(
            title = stringResource(R.string.new_agent_group),
            nameLabel = stringResource(R.string.agent_group_name),
            instructionsLabel = stringResource(R.string.shared_instructions),
            workspace = selectedAgentWorkspace,
            onSelectWorkspace = onSelectAgentWorkspace,
            onCreate = { name, instructions, workspace -> onCreateAgentGroup(name, instructions, workspace); addSheet = null },
            onDismiss = { addSheet = null }
        )
        else -> Unit
    }
}

@Composable
private fun FloatingPillTabBar(
    pagerState: PagerState
) {
    val coroutineScope = rememberCoroutineScope()
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    var tabLayouts by remember { mutableStateOf(List(SettingsScope.entries.size) { Rect.Zero }) }
    val density = LocalDensity.current

    Surface(
        shape = CircleShape,
        color = colors.groupSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val x = change.position.x
                    var closest = 0
                    var minDistance = Float.MAX_VALUE
                    tabLayouts.forEachIndexed { i, rect ->
                        if (rect != Rect.Zero) {
                            val dist = abs(rect.center.x - x)
                            if (dist < minDistance) {
                                minDistance = dist
                                closest = i
                            }
                        }
                    }
                    if (pagerState.targetPage != closest) {
                        coroutineScope.launch { pagerState.animateScrollToPage(closest) }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp).height(IntrinsicSize.Min)) {
            val currentTab = pagerState.currentPage
            val fraction = pagerState.currentPageOffsetFraction
            val actualNextTab = if (fraction > 0f) minOf(currentTab + 1, 2) else maxOf(currentTab - 1, 0)

            val currentRect = tabLayouts.getOrNull(currentTab) ?: Rect.Zero
            val nextRect = tabLayouts.getOrNull(actualNextTab) ?: Rect.Zero

            if (currentRect != Rect.Zero && nextRect != Rect.Zero) {
                val bgX = androidx.compose.ui.unit.lerp(
                    with(density) { currentRect.left.toDp() },
                    with(density) { nextRect.left.toDp() },
                    abs(fraction)
                )
                val bgWidth = androidx.compose.ui.unit.lerp(
                    with(density) { currentRect.width.toDp() },
                    with(density) { nextRect.width.toDp() },
                    abs(fraction)
                )

                Box(
                    modifier = Modifier
                        .offset(x = bgX)
                        .width(bgWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsScope.entries.forEachIndexed { index, scope ->
                    val pageOffset = pagerState.currentPage - index + pagerState.currentPageOffsetFraction
                    val visibilityFraction = (1f - abs(pageOffset)).coerceIn(0f, 1f)

                    val contentColor = lerp(colors.secondaryText, MaterialTheme.colorScheme.onPrimaryContainer, visibilityFraction)
                    val horizontalPadding = androidx.compose.ui.unit.lerp(12.dp, 20.dp, visibilityFraction)

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val rect = coords.boundsInParent()
                                if (tabLayouts[index] != rect) {
                                    val newLayouts = tabLayouts.toMutableList()
                                    newLayouts[index] = rect
                                    tabLayouts = newLayouts
                                }
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val icon = when (scope) {
                                SettingsScope.GLOBAL -> Icons.Default.Public
                                SettingsScope.PROJECT -> Icons.Default.FolderOpen
                                SettingsScope.AGENT -> Icons.Default.SmartToy
                            }
                            Icon(icon, contentDescription = stringResource(scope.label), tint = contentColor, modifier = Modifier.size(22.dp))

                            if (visibilityFraction > 0.01f) {
                                Text(
                                    text = stringResource(scope.label),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = contentColor,
                                    modifier = Modifier
                                        .padding(start = 8.dp * visibilityFraction)
                                        .graphicsLayer { alpha = visibilityFraction }
                                        .drawWithContent {
                                            drawContent()
                                            if (visibilityFraction < 0.99f && size.width > 0f) {
                                                val fadePx = 24.dp.toPx() * (1f - visibilityFraction)
                                                val leftStop = (fadePx / size.width).coerceIn(0f, 0.45f)
                                                val brush = Brush.horizontalGradient(
                                                    0f to Color.Transparent,
                                                    leftStop to Color.Black,
                                                    (1f - leftStop) to Color.Black,
                                                    1f to Color.Transparent
                                                )
                                                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                                            }
                                        }
                                        .clipToBounds()
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            val targetWidth = (placeable.width * visibilityFraction).toInt()
                                            layout(targetWidth, placeable.height) {
                                                val x = (targetWidth - placeable.width) / 2
                                                placeable.placeRelative(x, 0)
                                            }
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOwnerSheet(
    title: String,
    nameLabel: String,
    instructionsLabel: String,
    workspace: String?,
    onSelectWorkspace: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(title) { mutableStateOf("") }
    var instructions by remember(title) { mutableStateOf("") }
    StandardModalBottomSheet(onDismissRequest = onDismiss, title = title) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(nameLabel) }, singleLine = true)
        OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), label = { Text(instructionsLabel) }, minLines = 4)
        OutlinedButton(onClick = onSelectWorkspace, modifier = Modifier.fillMaxWidth()) { Text(workspace ?: stringResource(R.string.select_workspace)) }
        Button(
            onClick = { onCreate(name.trim(), instructions.trim(), workspace.orEmpty()) },
            enabled = name.isNotBlank() && !workspace.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.create)) }
    }
}

@Composable
private fun GlobalSettings(
    settings: AiSettings,
    onModels: () -> Unit,
    onAboutYou: () -> Unit,
    onMcp: () -> Unit,
    onSkills: () -> Unit,
    onTerminal: () -> Unit,
    updateStatus: String?,
    isDownloadingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onSnackbar: (String) -> Unit
) {
    com.amaya.intelligence.ui.screens.amaya.AmayaSection("AI") {
        val modelCount = settings.connections.sumOf { it.visibleModels.size }
        IosSettingsRow(
            Icons.Default.SmartToy,
            stringResource(R.string.manage_models),
            if (settings.connections.isEmpty()) stringResource(R.string.no_providers_configured) else stringResource(R.string.providers_and_models, settings.connections.size, modelCount),
            true,
            true,
            onModels
        )
    }
    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.knowledge)) {
        IosSettingsRow(Icons.Default.Person, stringResource(R.string.about_you), stringResource(R.string.global_saved_profile_and_preferences), true, false, onAboutYou)
        IosSettingsDivider()
        IosSettingsRow(Icons.Default.Psychology, stringResource(R.string.skills), stringResource(R.string.universal_reusable_workflows), false, true, onSkills)
    }
    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.execution)) {
        IosSettingsRow(Icons.Default.Terminal, stringResource(R.string.terminal_policy), stringResource(R.string.global_trusted_and_declined_commands), true, true, onTerminal)
    }
    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.integrations)) {
        val mcp = remember(settings.mcpConfigJson) { McpConfig.fromJson(settings.mcpConfigJson) }
        val active = mcp.servers.count { it.enabled }
        IosSettingsRow(
            Icons.Default.Extension,
            stringResource(R.string.mcp_servers),
            if (mcp.servers.isEmpty()) stringResource(R.string.no_servers_configured) else stringResource(R.string.active_mcp_servers, active, mcp.servers.size),
            true,
            true,
            onMcp
        )
    }
    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.appearance)) {
        IosThemeRow(settings.theme, onSelectTheme)
        IosSettingsDivider()
        IosLanguageRow()
    }
    var showUpdateSheet by remember(updateInfo) { mutableStateOf(updateInfo != null) }
    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.about)) {
        IosSettingsRow(Icons.Default.Info, stringResource(R.string.version), com.amaya.intelligence.BuildConfig.VERSION_NAME, true, false) {
            onSnackbar("Amaya Intelligence v${com.amaya.intelligence.BuildConfig.VERSION_NAME}")
        }
        IosSettingsDivider()
        IosSettingsRow(
            Icons.Default.SystemUpdate,
            if (updateInfo?.isNewer == true) stringResource(R.string.view_update) else stringResource(R.string.check_for_update),
            updateStatus ?: stringResource(R.string.check_github_releases_for_a_signed_apk),
            false,
            false
        ) {
            if (updateInfo != null) showUpdateSheet = true else onCheckForUpdate()
        }
        if (showUpdateSheet && updateInfo != null) {
            UpdateDetailsSheet(
                update = updateInfo,
                status = updateStatus,
                isDownloadingUpdate = isDownloadingUpdate,
                onDismiss = { showUpdateSheet = false },
                onDownload = onInstallUpdate,
                onCheckAgain = onCheckForUpdate
            )
        }
        IosSettingsDivider()
        val context = androidx.compose.ui.platform.LocalContext.current
        IosSettingsRow(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help_feedback), stringResource(R.string.help_feedback)_SUBTITLE, false, true) {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/nazrielnr/amaya/pulls")))
        }
    }
}

@Composable
private fun UpdateDetailsSheet(
    update: UpdateInfo,
    status: String?,
    isDownloadingUpdate: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCheckAgain: () -> Unit
) {
    StandardModalBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.update_version_title, update.tagName)) {
        Text(
            text = if (update.isNewer) stringResource(R.string.a_signed_apk_is_available) else stringResource(R.string.you_already_have_this_release),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.release_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = update.changelog.ifBlank { stringResource(R.string.no_release_notes_were_provided) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        status?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        val downloading = isDownloadingUpdate
        if (update.isNewer) {
            Button(onClick = onDownload, enabled = !downloading, modifier = Modifier.fillMaxWidth()) {
                Text(if (downloading) stringResource(R.string.downloading) else stringResource(R.string.download_and_install))
            }
        }
        OutlinedButton(onClick = onCheckAgain, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_again)) }
    }
}

@Composable
private fun ProjectListSettings(
    projects: List<ProjectEntity>,
    onOpenProject: (ProjectEntity) -> Unit
) {
    if (projects.isEmpty()) {
        com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
            title = stringResource(R.string.no_projects_yet),
            subtitle = stringResource(R.string.create_a_project_to_organize_workspace_instructions),
            icon = Icons.Default.FolderOpen,
            modifier = Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateTabTopSpacing)
        )
    } else {
        com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.projects)) {
            projects.forEachIndexed { index, project ->
                IosSettingsRow(Icons.Default.FolderOpen, project.name, project.rootPath, index == 0, false) { onOpenProject(project) }
                IosSettingsDivider()
            }
        }
    }
}

@Composable
private fun AgentListSettings(
    groups: List<AgentGroupEntity>,
    agents: List<AgentEntity>,
    onOpenGroup: (AgentGroupEntity) -> Unit
) {
    if (groups.isEmpty()) {
        com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
            title = stringResource(R.string.no_agent_groups_yet),
            subtitle = stringResource(R.string.create_a_group_then_add_specialized_agents_sentence),
            icon = Icons.Default.Groups,
            modifier = Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateTabTopSpacing)
        )
    } else {
        com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.agent_groups)) {
            groups.forEachIndexed { index, group ->
                val count = agents.count { it.groupId == group.id }
                IosSettingsRow(
                    Icons.Default.Groups,
                    group.name,
                    pluralStringResource(R.plurals.agents_in_workspace, count, count, group.workspacePath),
                    index == 0,
                    false
                ) { onOpenGroup(group) }
                IosSettingsDivider()
            }
        }
    }
}

@Composable
private fun IosStatusRow(title: String, subtitle: String) {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = colors.primaryText)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp), color = colors.secondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}



@Composable
private fun IosSettingsRow(icon: ImageVector, title: String, subtitle: String, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    val shape = when { isFirst && isLast -> RoundedCornerShape(16.dp); isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp); isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp); else -> RoundedCornerShape(0.dp) }
    Surface(onClick = onClick, shape = shape, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.iconTint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp), color = colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.secondaryText.copy(alpha = .55f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IosSettingsDivider() {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    HorizontalDivider(Modifier.padding(start = 58.dp), thickness = .7.dp, color = colors.separator)
}

@Composable
private fun IosThemeRow(selectedTheme: String, onSelectTheme: (String) -> Unit) {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Palette, null, tint = colors.iconTint)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge, color = colors.primaryText)
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val values = listOf("system" to stringResource(R.string.system), "light" to stringResource(R.string.light), "dark" to stringResource(R.string.dark))
            values.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = selectedTheme == value, onClick = { onSelectTheme(value) }, shape = SegmentedButtonDefaults.itemShape(index, values.size)) { Text(label) }
            }
        }
    }
}

@Composable
private fun IosLanguageRow() {
    var showPicker by remember { mutableStateOf(false) }
    val selectedLanguage = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language
    val options = listOf(
        "" to stringResource(R.string.follow_system),
        "zh-CN" to stringResource(R.string.chinese_simplified),
        "en" to stringResource(R.string.english)
    )
    val selectedTag = when (selectedLanguage) {
        "zh" -> "zh-CN"
        "en" -> "en"
        else -> ""
    }
    IosSettingsRow(
        Icons.Default.Language,
        stringResource(R.string.language),
        options.first { it.first == selectedTag }.second,
        false,
        true
    ) { showPicker = true }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    options.forEach { (tag, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = tag == selectedTag,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                    onClick = {
                                        showPicker = false
                                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                                        )
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tag == selectedTag, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
