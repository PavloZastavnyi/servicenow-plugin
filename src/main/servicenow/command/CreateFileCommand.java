package main.servicenow.command;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.command.WriteCommandAction;

public class CreateFileCommand {
    public static PsiFile createTempPsiFile(@NotNull final Project project, @NotNull final String text, @NotNull final String filename, final PsiDirectory directory) {
        String[] arr = filename.split(":");
        if (arr.length < 2) {
            System.exit(1);
        }

        String sys_id = arr[0];
        String field = arr[1];

        try {
            final PsiFile[] file = new PsiFile[1];
            final PsiFile[] ffile = new PsiFile[1];
            WriteCommandAction.runWriteCommandAction(project, () -> {
                file[0] = PsiFileFactory.getInstance(project).createFileFromText(sys_id + "." + field + ".js",
                        StdFileTypes.JS,
                        text, LocalTimeCounter.currentTime(), true);
                ffile[0] = (PsiFile) directory.add(file[0]);
            });
            return ffile[0];
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
