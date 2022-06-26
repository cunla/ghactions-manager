// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.dsoftware.ghtoolbar.ui.settings

import com.dsoftware.ghtoolbar.ui.ToolbarUtil
import com.dsoftware.ghtoolbar.ui.settings.ToolbarSettings.RepoSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager


internal class GhActionsToolbarConfigurable internal constructor(
    project: Project
) : BoundConfigurable(ToolbarUtil.SETTINGS_DISPLAY_NAME, "settings.ghactions-toolbar") {
    private val ghActionsSettingsService = GhActionsSettingsService.getInstance(project)

    private val state = ghActionsSettingsService.state

    private val repoManager = project.service<GHProjectRepositoriesManager>()

    override fun apply() {
        super.apply()
        ApplicationManager.getApplication().messageBus.syncPublisher(SETTINGS_CHANGED).settingsChanged()
    }

    override fun createPanel(): DialogPanel {
        val knownRepositories = repoManager.knownRepositories
        return panel {
            lateinit var projectRepos: Cell<JBCheckBox>
            row {
                projectRepos = checkBox("Use custom repositories")
                    .comment("Do not use all repositories in the project")
                    .bindSelected(state::useCustomRepos, state::useCustomRepos::set)
            }

            group {
                twoColumnsRow({ label("Repository") }, { label("Selected") })
                knownRepositories
                    .map { it.gitRemoteUrlCoordinates.url }
                    .forEach {
                        val settingsValue = state.customRepos.getOrPut(it) { RepoSettings() }
                        twoColumnsRow(
                            { text(it) },
                            {
                                checkBox("")
                                    .bindSelected(settingsValue::included, settingsValue::included::set)
                            })
                    }
            }.enabledIf(projectRepos.selected)
        }

    }

    interface SettingsChangedListener {
        fun settingsChanged()
    }

    companion object {
        @JvmField
        @Topic.AppLevel
        val SETTINGS_CHANGED = Topic(SettingsChangedListener::class.java, Topic.BroadcastDirection.NONE)

    }
}


