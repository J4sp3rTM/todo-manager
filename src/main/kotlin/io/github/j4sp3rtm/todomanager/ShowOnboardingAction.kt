package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * Re-opens the [OnboardingDialog] on demand (Help &rsaquo; TODO Manager: Getting Started), so users
 * can revisit the tour after it has been shown automatically on first run.
 */
class ShowOnboardingAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        OnboardingDialog(project).show()
    }
}
