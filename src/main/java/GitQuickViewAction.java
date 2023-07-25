import com.esotericsoftware.minlog.Log;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class GitQuickViewAction extends DumbAwareAction {

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
        Log.info("Temporary directory created successfully");

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
                    cloneGitRepository(gitUrl, tempDir);
                    Log.info("Git repository cloned successfully to: " + tempDir.getAbsolutePath());

                    File repositoryDirectory = locateGitRepository(tempDir);
                    String repoPath = repositoryDirectory.getAbsolutePath();

                    Project newProject = openProjectInIDEA(repoPath, project);
                    Log.info("Project opened successfully in directory: " + repoPath);

                    registerProjectCleanupOnClose(newProject, tempDir);

                    removeFromRecentProjectsList(repoPath);
                    Log.info("Project removed from the recent projects list");

                } catch (IOException | InterruptedException ex) {
                    throw new RuntimeException("Failed to clone git repository", ex);
                }
            }
        });
    }

    private void cloneGitRepository(String gitUrl, File tempDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl);
        pb.directory(tempDir);
        Process p = pb.start();
        p.waitFor();
    }

    private File locateGitRepository(File tempDir) {
        File[] files = tempDir.listFiles();

        return Arrays.stream(files)
                .filter(File::isDirectory)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No directory found in tempDir"));
    }

    private Project openProjectInIDEA(String repoPath, Project currentProject) {
        return ProjectUtil.openOrImport(repoPath, currentProject, true);
    }

    private void registerProjectCleanupOnClose(Project newProject, File tempDir) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

        connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                if (project.equals(newProject)) {
                    scheduleProjectDirectoryDeletion(tempDir);
                    connection.disconnect();
                }
            }
        });
    }

    private void scheduleProjectDirectoryDeletion(File tempDir) {
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Sleep for a bit to give IntelliJ time to finish its operations
                Files.delete(tempDir.toPath());
                Log.info("Temporary directory deleted successfully: " + tempDir.getAbsolutePath());
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void removeFromRecentProjectsList(String repoPath) {
        RecentProjectsManager manager = RecentProjectsManager.getInstance();
        manager.removePath(repoPath);
    }
}
