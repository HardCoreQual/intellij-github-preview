import com.esotericsoftware.minlog.Log;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBusConnection;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;


public class CloneForPreviewAction extends DumbAwareAction {
    private static final Logger LOG = Logger.getInstance(CloneForPreviewAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project currentProject = e.getProject();

        String url = Messages.showInputDialog(
                currentProject,
                "Enter the GitHub repository URL",
                "Open from GitHub",
                null
        );

        File tempDir;
        try {
            tempDir = Files.createTempDirectory("MyPlugin").toFile();
        } catch (IOException ex) {
            // TODO: handle the exception
            throw new RuntimeException(ex);
        }

        Log.info("Successfully created temporary directory");

        // Clone the repository with depth 1
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", url);
            pb.directory(tempDir);
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            // TODO: handle the exception
        }

        Log.info("Successfully cloned the repository in the temporary directory: " + tempDir.getAbsolutePath());

        File[] files = tempDir.listFiles();

        // Get the first directory in the temp directory. This assumes that the cloned repo is the only thing in the temp directory.
        File repoDir = Arrays.stream(files)
                .filter(File::isDirectory)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No directory found in tempDir"));

        String repoPath = repoDir.getAbsolutePath();

        Project newProject = ProjectUtil.openOrImport(repoPath, currentProject, true);

        Log.info("Successfully opened the project in directory: " + repoPath);

        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

        connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(Project project) {
                if (project.equals(newProject)) {
                    new Thread(() -> {
                        try {
                            // Sleep for a bit to give IntelliJ time to finish its operations
                            Thread.sleep(2000); // 2 seconds
                            FileUtils.deleteDirectory(tempDir);

                            Log.info("Successfully deleted the temporary directory: " + tempDir.getAbsolutePath());
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    // Disconnect the message bus connection
                    connection.disconnect();
                }
            }
        });

        RecentProjectsManager manager = RecentProjectsManager.getInstance();
        manager.removePath(repoPath);

        Log.info("Successfully removed the project from the recent projects list");

    }
}
