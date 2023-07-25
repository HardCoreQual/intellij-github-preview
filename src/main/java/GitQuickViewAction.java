import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class GitQuickViewAction extends DumbAwareAction {
    private static final Logger LOG = Logger.getInstance(GitQuickViewAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project currentProject = event.getProject();

        String gitUrl = Messages.showInputDialog(
                currentProject,
                "Enter the Git repository URL",
                "Open Git QuickView",
                null
        );

        File tempDir = createTempDirectory();

        LOG.info("Temporary directory created successfully");

        cloneRepositoryAndOpenProject(currentProject, gitUrl, tempDir);
    }

    private File createTempDirectory() {
        try {
            return Files.createTempDirectory("GitQuickView").toFile();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create temporary directory", ex);
        }
    }

    private void cloneRepositoryAndOpenProject(Project project, String gitUrl, File tempDir) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Cloning Git repository", false) {
            @Override
            public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl);
                    pb.directory(tempDir);
                    Process p = pb.start();
                    p.waitFor();

                    LOG.info("Git repository cloned successfully to: " + tempDir.getAbsolutePath());

                    File[] files = tempDir.listFiles();
                    assert files != null;
                    File repositoryDirectory = Arrays.stream(files)
                            .filter(File::isDirectory)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No directory found in tempDir"));

                    String repoPath = repositoryDirectory.getAbsolutePath();

                    Project newProject = ProjectUtil.openOrImport(repoPath, project, true);
                    LOG.info("Project opened successfully in directory: " + repoPath);

                    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
                    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
                        @Override
                        public void projectClosing(@NotNull Project project) {
                            if (project.equals(newProject)) {
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(2000); // Sleep for a bit to give IntelliJ time to finish its operations
                                        FileUtil.delete(tempDir.toPath());
                                        LOG.info("Temporary directory deleted successfully: " + tempDir.getAbsolutePath());
                                    } catch (InterruptedException | IOException e) {
                                        throw new RuntimeException("Failed to delete temporary directory", e);
                                    }
                                }).start();

                                RecentProjectsManager.getInstance().removePath(repoPath);
                                connection.disconnect();
                            }
                        }
                    });

                    LOG.info("Project removed from the recent projects list");

                } catch (IOException | InterruptedException ex) {
                    throw new RuntimeException("Failed to clone git repository", ex);
                }
            }
        });
    }
}
