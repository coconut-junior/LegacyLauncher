/*
 * This file is part of Spoutcraft Launcher (http://wiki.getspout.org/).
 * 
 * Spoutcraft Launcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spoutcraft Launcher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.spoutcraft.launcher;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.spoutcraft.launcher.io.ProfileResponse;
import org.spoutcraft.launcher.io.SessionProfileResponse;
import org.spoutcraft.launcher.io.SkinInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.JProgressBar;

public class PlatformUtils {

  public static final String LAUNCHER_DIR = "techniclauncher";
  private static File        workDir      = null;
  private static final String PROFILE_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
  private static final String SESSION_API_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

  public static File getWorkingDirectory() {
    if (workDir == null) {
      workDir = getWorkingDirectory(LAUNCHER_DIR);
    }
    return workDir;
  }

  public static File getWorkingDirectory(String applicationName) {
    boolean isPortable = MinecraftUtils.getOptions().isPortable();
    if (isPortable) {
      return new File("." + LAUNCHER_DIR);
    }
    String userHome = System.getProperty("user.home", ".");
    File workingDirectory;
    switch (getPlatform()) {
    case linux:
    case solaris:
      workingDirectory = new File(userHome, '.' + applicationName + '/');
      break;
    case windows:
      String applicationData = System.getenv("APPDATA");
      if (applicationData != null) {
        workingDirectory = new File(applicationData, "." + applicationName + '/');
      } else {
        workingDirectory = new File(userHome, '.' + applicationName + '/');
      }
      break;
    case macos:
      workingDirectory = new File(userHome, "Library/Application Support/" + applicationName);
      break;
    default:
      workingDirectory = new File(userHome, applicationName + '/');
    }
    if ((!workingDirectory.exists()) && (!workingDirectory.mkdirs())) {
      throw new RuntimeException("The working directory could not be created: " + workingDirectory);
    }
    return workingDirectory;
  }

  public static OS getPlatform() {
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("win")) {
      return OS.windows;
    }
    if (osName.contains("mac")) {
      return OS.macos;
    }
    if (osName.contains("solaris")) {
      return OS.solaris;
    }
    if (osName.contains("sunos")) {
      return OS.solaris;
    }
    if (osName.contains("linux")) {
      return OS.linux;
    }
    if (osName.contains("unix")) {
      return OS.linux;
    }
    return OS.unknown;
  }

  public enum OS {
    linux, solaris, windows, macos, unknown
  }

  public static String excutePost(String url, String data, JProgressBar progress) throws IOException {
    byte[] rawData = data.getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setUseCaches(false);
    connection.setDoOutput(true);
    connection.setDoInput(true);
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(15000);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("Content-Length", Integer.toString(rawData.length));
    connection.setRequestProperty("Content-Language", "en-US");

    DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
    writer.write(rawData);
    writer.flush();
    writer.close();

    InputStream stream = null;
    String returnable = null;
    try {
      stream = connection.getInputStream();
      returnable = IOUtils.toString(stream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      stream = connection.getErrorStream();

      if (stream == null) {
        throw e;
      }
    } finally {
      try {
        if (stream != null)
          stream.close();
      } catch (IOException ignored) {}
    }

    return returnable;
  }

  public static String getUserSkin(String username) {
    try {
      ProfileResponse userProfile = executeGetRequest(PROFILE_API_URL+username, ProfileResponse.class);
      if (userProfile == null) return null;
      SessionProfileResponse session = executeGetRequest(SESSION_API_URL+userProfile.getId(), SessionProfileResponse.class);
      String encoded = session.getProperties().get(0).getValue();
      Base64.Decoder decoder = Base64.getDecoder();
      String decoded = new String(decoder.decode(encoded));
      SkinInfo skin = new Gson().fromJson(decoded, SkinInfo.class);
      return skin.getTextures().getSKIN().getUrl();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static <T> T executeGetRequest(String url, Class<T> classType) throws IOException {
    URL obj = new URL(url);
    HttpURLConnection httpURLConnection = (HttpURLConnection) obj.openConnection();
    httpURLConnection.setRequestMethod("GET");
    httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
    if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
      InputStreamReader in = new InputStreamReader(httpURLConnection.getInputStream());

      return new Gson().fromJson(in, classType);
    }
    return null;
  }
}
