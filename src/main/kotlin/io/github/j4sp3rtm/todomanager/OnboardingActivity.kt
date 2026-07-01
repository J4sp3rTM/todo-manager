package io.github.j4sp3rtm.todomanager

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On the first run after installation, invites the user into the [OnboardingDialog] tour.
 *
 * The invitation is a non-modal notification, not an auto-shown dialog: showing a modal dialog from
 * a startup activity blocks the EDT (and hangs headless/CI plugin-install checks, which have nothing
 * to dismiss it). The user opens the tour by clicking the notification action, or later via the
 * "TODO Manager: Getting Started" action.
 *
 * The "shown" flag is stored application-wide (via [PropertiesComponent]) so this appears only once
 * per install, not once per project. Setting the flag before notifying also guards against two
 * projects opening at once both trying to show it.
 */
class OnboardingActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment) return

        val props = PropertiesComponent.getInstance()
        if (props.getBoolean(SHOWN_KEY, false)) return
        props.setValue(SHOWN_KEY, true)

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                "TODO Highlighter & Comment Manager",
                "Take a quick tour to see how it works.",
                NotificationType.INFORMATION,
            )
        notification.addAction(NotificationAction.createSimple("Start tour") {
            notification.expire()
            if (!project.isDisposed) OnboardingDialog(project).show()
        })
        notification.notify(project)
    }

    companion object {
        const val SHOWN_KEY = "io.github.j4sp3rtm.todomanager.onboarding.shown"
        const val NOTIFICATION_GROUP = "TODO Manager Onboarding"
    }
}
