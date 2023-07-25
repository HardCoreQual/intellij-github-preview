import com.esotericsoftware.minlog.Log;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
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
        Log.info("Successfully created temporary directory");

        cloneGitRepo(gitUrl, tempDir);
        Log.info("Successfully cloned the repository in the temporary directory: " + tempDir.getAbsolutePath());

        File repoDir = getRepoDirectory(tempDir);
        String repoPath = repoDir.getAbsolutePath();

        Project newProject = ProjectUtil.openOrImport(repoPath, currentProject, true);
        Log.info("Successfully opened the project in directory: " + repoPath);

        cleanUpOnProjectClose(newProject, tempDir);

        removeProjectFromRecentList(repoPath);
        Log.info("Successfully removed the project from the recent projects list");
    }

    private File createTempDirectory() {
        try {
            return Files.createTempDirectory("GitQuickView").toFile();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create temporary directory", ex);
        }
    }

    private void cloneGitRepo(String gitUrl, File tempDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl);
            pb.directory(tempDir);
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Failed to clone git repository", ex);
        }
    }

    private File getRepoDirectory(File tempDir) {
        File[] files = tempDir.listFiles();

        return Arrays.stream(files)
                .filter(File::isDirectory)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No directory found in tempDir"));
    }

    private void cleanUpOnProjectClose(Project newProject, File tempDir) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

        connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                if (project.equals(newProject)) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000); // Sleep for a bit to give IntelliJ time to finish its operations
                            Files.delete(tempDir.toPath());
                            Log.info("Successfully deleted the temporary directory: " + tempDir.getAbsolutePath());
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    connection.disconnect();
                }
            }
        });
    }

    private void removeProjectFromRecentList(String repoPath) {
        RecentProjectsManager manager = RecentProjectsManager.getInstance();
        manager.removePath(repoPath);
    }
}
