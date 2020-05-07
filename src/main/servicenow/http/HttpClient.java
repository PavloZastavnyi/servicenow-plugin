package main.servicenow.http;

import com.google.gson.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import main.servicenow.notificationmanager.Notification;
import main.servicenow.command.CreateFileCommand;
import main.servicenow.config.Config;
import okhttp3.*;
import javax.swing.*;
import java.io.IOException;
import com.google.gson.JsonParser;

public class HttpClient {
    private OkHttpClient client;

    public HttpClient(Project project) {
        VirtualFile baseDir = project.getBaseDir();
        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(baseDir);
        Boolean configExists = Config.exists(directory);
        JsonObject jsObj = null;
        if (configExists) {
            PsiFile file = Config.read(directory);
            jsObj = new JsonParser().parse(file.getText()).getAsJsonObject();
        }
        assert jsObj != null;

        JsonObject finalJsObj = jsObj;
        client = new OkHttpClient.Builder()
                .authenticator((route, response) -> {
                    String credential = Credentials.basic(finalJsObj.get("username").getAsString(), finalJsObj.get("password").getAsString());
                    if (credential.equals(response.request().header("Service-now"))) {
                        return null; // If we already failed with these credentials, don't retry.
                    }
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                })
                .build();
    }

    public void DownloadScript(String filename, Project project, PsiDirectory directory) throws Exception {
        String[] arr = filename.split(":");
        if (arr.length < 3) {
            SwingUtilities.invokeLater(() -> Notification.showNotification(project, "Invalid parameter count! Please do it like described in the modal window!", MessageType.ERROR, 3500));
            return;
        }

        String table = arr[0];
        String sys_id = arr[1];
        String field = arr[2];

        //read config
        Boolean configExists = Config.exists(directory);
        JsonObject jsObj = null;
        if (configExists) {
            PsiFile file = Config.read(directory);
            jsObj = new JsonParser().parse(file.getText()).getAsJsonObject();
        }

        PsiDirectory scriptDirectory = directory.findSubdirectory(table) == null ? directory.createSubdirectory(table) : directory.findSubdirectory(table);
        String scriptFilename = sys_id + ':' + field;

        try {
            assert jsObj != null;
            String url = "https://" + jsObj.get("domain").getAsString() + "/api/now/table/" + table + "?sysparm_query=sys_id%3D" + sys_id + "&sysparm_fields=" + field + "&sysparm_limit=1";

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    String script = new JsonParser().parse(response.body().string()).getAsJsonObject().getAsJsonArray("result").get(0).getAsJsonObject().get(field).getAsString();

                    SwingUtilities.invokeLater(() -> {
                        PsiFile pFile = CreateFileCommand.createTempPsiFile(project, script.replace("\r\n", "\n"), scriptFilename, scriptDirectory);
                        if (pFile != null) {
                            pFile.navigate(true);
                        }
                    });

                    response.body().close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void put(String filename, String code, Project project, Integer timeout) throws Exception {
        String[] arr = filename.split("\\.");
        if (arr.length < 3) {
            SwingUtilities.invokeLater(() -> Notification.showNotification(project, "Invalid filename, e.g. you did not download the script with this plugin!", MessageType.ERROR, timeout));
            return;
        }

        String table = arr[0];
        String sys_id = arr[1];
        String field = arr[2];

        //read config
        VirtualFile baseDir = project.getBaseDir();
        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(baseDir);
        Boolean configExists = Config.exists(directory);
        JsonObject jsObj = null;

        if (configExists) {
            PsiFile file = Config.read(directory);
            jsObj = new JsonParser().parse(file.getText()).getAsJsonObject();
        }

        assert jsObj != null;

        String url = "https://" + jsObj.get("domain").getAsString() + "/api/now/table/" + table + "/" + sys_id + "?sysparm_fields=" + field;
        JsonObject obj = new JsonObject();
        obj.addProperty(field, code);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(JSON, String.valueOf(obj));

        Request request = new Request.Builder()
                .put(body)
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                SwingUtilities.invokeLater(() -> Notification.showNotification(project, "There was some error while uploading your script! Try again later!", MessageType.ERROR, timeout));
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                SwingUtilities.invokeLater(() -> Notification.showNotification(project, "Script uploaded successfully", MessageType.INFO, timeout));
                response.body().close();
            }
        });
    }
}
