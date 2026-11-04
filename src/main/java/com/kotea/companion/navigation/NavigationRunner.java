package com.kotea.companion.navigation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.util.ContextPresentationProvider;
import com.kotea.companion.util.PerfLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Searches for the sites of a KoTEA class that play a {@link Role} in the background, then navigates to them.
 */
public final class NavigationRunner {

    private static final Logger LOG = Logger.getInstance(NavigationRunner.class);

    /** Where the outcome of a navigation is shown. */
    public interface Presenter {
        void showMessage(@NotNull String message);

        void showPopup(@NotNull JBPopup popup);
    }

    private NavigationRunner() {
    }

    public static String title(@NotNull Role role) {
        return "Go to " + displayName(role);
    }

    public static void navigate(@NotNull KtClassOrObject target, @NotNull Role role, @NotNull Presenter presenter) {
        String lockKey = lockKey(target, role);
        if (!SearchLock.tryLock(lockKey)) {
            presenter.showMessage("Search already in progress");
            return;
        }

        Project project = target.getProject();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title(role), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    long start = PerfLog.start();
                    List<PsiElement> targets = ReadAction.compute(() -> EmissionOrProcessingSearcher.find(target, role));
                    PerfLog.logElapsed(LOG, displayName(role) + " search", start);
                    if (indicator.isCanceled()) return;

                    ApplicationManager.getApplication().invokeLater(() -> show(targets, role, project, presenter));
                } finally {
                    SearchLock.unlock(lockKey);
                }
            }
        });
    }

    public static Presenter inEditor(@NotNull Editor editor) {
        return new Presenter() {
            @Override
            public void showMessage(@NotNull String message) {
                HintManager.getInstance().showInformationHint(editor, message);
            }

            @Override
            public void showPopup(@NotNull JBPopup popup) {
                popup.showInBestPositionFor(editor);
            }
        };
    }

    public static Presenter atMouse(@NotNull MouseEvent mouseEvent) {
        return new Presenter() {
            @Override
            public void showMessage(@NotNull String message) {
                JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder(message, MessageType.INFO, null)
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(new RelativePoint(mouseEvent), Balloon.Position.atRight);
            }

            @Override
            public void showPopup(@NotNull JBPopup popup) {
                popup.show(new RelativePoint(mouseEvent));
            }
        };
    }

    private static void show(List<PsiElement> targets, Role role, Project project, Presenter presenter) {
        if (targets.isEmpty()) {
            presenter.showMessage("No " + displayName(role).toLowerCase() + " usages found");
        } else if (targets.size() == 1) {
            ((Navigatable) targets.getFirst()).navigate(true);
        } else {
            presenter.showPopup(new PsiTargetNavigator<>(targets)
                    .presentationProvider(ContextPresentationProvider::getPresentation)
                    .createPopup(project, title(role)));
        }
    }

    private static String lockKey(KtClassOrObject target, Role role) {
        var fqName = target.getFqName();
        return (fqName != null ? fqName.asString() : String.valueOf(System.identityHashCode(target))) + ":" + role;
    }

    private static String displayName(Role role) {
        return switch (role) {
            case EMISSION -> "Emission";
            case PROCESSING -> "Processing";
        };
    }

    private static final class SearchLock {
        private static final Set<String> active = ConcurrentHashMap.newKeySet();

        private SearchLock() {}

        public static boolean tryLock(String key) {
            return active.add(key);
        }

        public static void unlock(String key) {
            active.remove(key);
        }
    }
}
