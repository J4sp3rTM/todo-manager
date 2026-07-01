package io.github.j4sp3rtm.todomanager

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Shows the [OnboardingDialog] once, the first time the plugin runs after installation.
 *
 * The "shown" flag is stored application-wide (via [PropertiesComponent]) so the tour appears only
 * once per install, not once per project. Setting the flag before showing the dialog also guards
 * against two projects opening at the same time both trying to show it.
 */
class OnboardingActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val props = PropertiesComponent.getInstance()
        if (props.getBoolean(SHOWN_KEY, false)) return
        props.setValue(SHOWN_KEY, true)

        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) OnboardingDialog(project).show()
        }) { project.isDisposed }
    }

    companion object {
        const val SHOWN_KEY = "io.github.j4sp3rtm.todomanager.onboarding.shown"
    }
}
