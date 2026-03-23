package com.amaya.intelligence.impl.ide

import com.amaya.intelligence.domain.ai.IdeProvider
import com.amaya.intelligence.domain.models.IdeInfo

/**
 * Factory for IDE providers using the Plugin pattern.
 * New IDEs register themselves here.
 */
object IdeProviderFactory {
    private val providers = mutableMapOf<String, IdeProvider>()
    
    init {
        // Register default providers
        register(com.amaya.intelligence.impl.ide.antigravity.AntigravityProvider)
        register(com.amaya.intelligence.impl.ide.cursor.CursorProvider)
        register(com.amaya.intelligence.impl.ide.windsurf.WindsurfProvider)
    }
    
    fun register(provider: IdeProvider) {
        providers[provider.ideId] = provider
    }
    
    fun get(ideId: String): IdeProvider? = providers[ideId]
    
    fun getAll(): List<IdeProvider> = providers.values.toList()
    
    fun getEnabled(): List<IdeProvider> = providers.values.filter { it.isEnabled }
    
    fun getIdeInfo(ideId: String): IdeInfo? = providers[ideId]?.info
}
