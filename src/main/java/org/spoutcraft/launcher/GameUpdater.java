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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.progress.ProgressMonitor;

import org.spoutcraft.launcher.async.DownloadListener;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GameUpdater implements DownloadListener {
  public static final String LAUNCHER_DIRECTORY = "launcher";
  public static final File   WORKING_DIRECTORY  = PlatformUtils.getWorkingDirectory();

  /* Minecraft Updating Arguments */
  public String              user               = "Player";
  public String              downloadTicket     = "1";

  /* Files */
  public static File         modpackDir         = new File(WORKING_DIRECTORY, "");
  public static File         binDir             = new File(WORKING_DIRECTORY, "bin");
  public static final File   cacheDir           = new File(WORKING_DIRECTORY, "cache");
  public static final File   tempDir            = new File(WORKING_DIRECTORY, "temp");
  public static File         backupDir          = new File(WORKING_DIRECTORY, "backups");
  public static final File   workDir            = new File(WORKING_DIRECTORY, LAUNCHER_DIRECTORY);
  public static File         savesDir           = new File(WORKING_DIRECTORY, "saves");
  public static File         modsDir            = new File(WORKING_DIRECTORY, "mods");
  public static File         libsDir            = new File(WORKING_DIRECTORY, "lib");
  public static File         coremodsDir        = new File(WORKING_DIRECTORY, "coremods");
  public static File         modconfigsDir      = new File(WORKING_DIRECTORY, "config");
  public static File         resourceDir        = new File(WORKING_DIRECTORY, "resources");

  /* Minecraft Updating Arguments */
  public final String        spoutcraftMirrors  = "https://cdn.getspout.org/mirrors.html";
  

  private DownloadListener listener;
  
  private JsonObject getVersionJson(String minecraftVersion) throws Exception {
    // 1. Get the version manifest
    JsonParser parser = new JsonParser();
    URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
    HttpURLConnection manifestConn = (HttpURLConnection) manifestUrl.openConnection();
    JsonObject manifest = parser.parse(new InputStreamReader(manifestConn.getInputStream(), StandardCharsets.UTF_8))
        .getAsJsonObject();

    // 2. Find the version's JSON URL
    String versionJsonUrl = null;
    for (JsonElement version : manifest.getAsJsonArray("versions")) {
      JsonObject v = version.getAsJsonObject();
      if (v.get("id").getAsString().equals(minecraftVersion)) {
        versionJsonUrl = v.get("url").getAsString();
        break;
      }
    }
    if (versionJsonUrl == null) {
      throw new RuntimeException("Version not found in manifest: " + minecraftVersion);
    }

    // 3. Fetch the version JSON
    URL versionUrl = new URL(versionJsonUrl);
    HttpURLConnection versionConn = (HttpURLConnection) versionUrl.openConnection();
    return parser.parse(new InputStreamReader(versionConn.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
  }

  public String getClientJarUrl(String minecraftVersion) throws Exception {
        JsonObject versionJson = getVersionJson(minecraftVersion);
        return versionJson.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
    }
    
  public String[] getLwjglUrls(String minecraftVersion) throws Exception {
        JsonObject versionJson = getVersionJson(minecraftVersion);
        List<String> lwjglUrls = new ArrayList<>();
        for (JsonElement libElem : versionJson.getAsJsonArray("libraries")) {
            JsonObject lib = libElem.getAsJsonObject();
            if (lib.getAsJsonObject("downloads") != null && lib.get("name").getAsString().contains("lwjgl")) {
                JsonObject artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                if (artifact != null && artifact.has("url")) {
                    lwjglUrls.add(artifact.get("url").getAsString());
                }
            }
        }
        return lwjglUrls.toArray(new String[0]);
    }

  public GameUpdater() {
  }

  public static void setModpackDirectory(String currentModPack) {
    modpackDir = new File(WORKING_DIRECTORY, currentModPack);
    modpackDir.mkdirs();

    binDir = new File(modpackDir, "bin");
    backupDir = new File(modpackDir, "backups");
    savesDir = new File(modpackDir, "saves");
    modsDir = new File(modpackDir, "mods");
    libsDir = new File(modpackDir, "lib");
    coremodsDir = new File(modpackDir, "coremods");
    modconfigsDir = new File(modpackDir, "config");
    resourceDir = new File(modpackDir, "resources");

    binDir.mkdirs();
    backupDir.mkdirs();
    savesDir.mkdirs();
    modsDir.mkdirs();
    libsDir.mkdirs();
    coremodsDir.mkdirs();
    modconfigsDir.mkdirs();
    resourceDir.mkdirs();

    System.setProperty("minecraft.applet.TargetDirectory", modpackDir.getAbsolutePath());
  }

  public void updateMC() throws Exception {

    binDir.mkdir();
    cacheDir.mkdirs();
    tempDir.mkdirs();

    ModpackBuild build = ModpackBuild.getSpoutcraftBuild();
    String minecraftVersion = build.getMinecraftVersion();

    // Download Minecraft client.jar dynamically
    File mcCache = new File(cacheDir, "minecraft_" + minecraftVersion + ".jar");
    if (!mcCache.exists()) {
      String clientJarUrl = getClientJarUrl(minecraftVersion);
      String output = tempDir + File.separator + "minecraft.jar";
      MinecraftDownloadUtils.downloadMinecraft(clientJarUrl, output, build, listener);
    }
    stateChanged("Copying minecraft.jar from cache", 0);
    copy(mcCache, new File(binDir, "minecraft.jar"));
    stateChanged("Copied minecraft.jar from cache", 100);

    // Download LWJGL libraries dynamically
    String[] lwjglUrls = getLwjglUrls(minecraftVersion);
    for (String lwjglUrl : lwjglUrls) {
        String fileName = lwjglUrl.substring(lwjglUrl.lastIndexOf('/') + 1);
        File libCache = new File(cacheDir, fileName);
        if (!libCache.exists()) {
            DownloadUtils.downloadFile(lwjglUrl, libCache.getAbsolutePath(), fileName, null, listener);
        } else {
            stateChanged("Copying " + fileName + " from cache", 0);
        }

        // Determine the expected output name
        String expectedName = null;
        if (fileName.contains("lwjgl_util")) {
            expectedName = "lwjgl_util.jar";
        } else if (fileName.contains("lwjgl") && !fileName.contains("util")) {
            expectedName = "lwjgl.jar";
        } else if (fileName.contains("jinput")) {
            expectedName = "jinput.jar";
        }

        if (expectedName != null) {
            File outFile = new File(binDir, expectedName);
            copy(libCache, outFile);
            stateChanged("Copied " + expectedName + " from cache", 100);
        }
    }

    File nativesDirectory = new File(binDir, "natives");
    downloadAndExtractNatives(minecraftVersion, nativesDirectory);

    MinecraftYML.setInstalledVersion(minecraftVersion);
  }
  
  // Example method to get native library URLs for macOS
  public List<String> getNativeLibraryUrls(String minecraftVersion) {
    List<String> nativeUrls = new ArrayList<>();
    try {
      // 1. Get the version JSON (reuse your getMinecraftURL logic)
      URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
      HttpURLConnection manifestConn = (HttpURLConnection) manifestUrl.openConnection();
      JsonParser parser = new JsonParser();
      JsonObject manifest = parser.parse(new InputStreamReader(manifestConn.getInputStream(), StandardCharsets.UTF_8))
          .getAsJsonObject();

      String versionJsonUrl = null;
      for (JsonElement version : manifest.getAsJsonArray("versions")) {
        JsonObject v = version.getAsJsonObject();
        if (v.get("id").getAsString().equals(minecraftVersion)) {
          versionJsonUrl = v.get("url").getAsString();
          break;
        }
      }
      if (versionJsonUrl == null) {
        throw new RuntimeException("Version not found in manifest: " + minecraftVersion);
      }

      URL versionUrl = new URL(versionJsonUrl);
      HttpURLConnection versionConn = (HttpURLConnection) versionUrl.openConnection();
      JsonObject versionJson = parser.parse(new InputStreamReader(versionConn.getInputStream(), StandardCharsets.UTF_8))
          .getAsJsonObject();

      // 2. Find native libraries for macOS
      for (JsonElement libElem : versionJson.getAsJsonArray("libraries")) {
        JsonObject lib = libElem.getAsJsonObject();
        if (lib.has("natives") && lib.getAsJsonObject("natives").has("osx")) {
          String classifier = lib.getAsJsonObject("natives").get("osx").getAsString();
          JsonObject downloads = lib.getAsJsonObject("downloads");
          if (downloads.has("classifiers") && downloads.getAsJsonObject("classifiers").has(classifier)) {
            String url = downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier).get("url").getAsString();
            nativeUrls.add(url);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to get native library URLs: " + e.getMessage(), e);
    }
    return nativeUrls;
  }

  public void downloadAndExtractNatives(String minecraftVersion, File nativesDir) throws Exception {
    List<String> nativeUrls = getNativeLibraryUrls(minecraftVersion);

    // Ensure natives directory exists
    if (!nativesDir.exists()) {
        nativesDir.mkdirs();
    }

    for (String nativeUrl : nativeUrls) {
        String fileName = nativeUrl.substring(nativeUrl.lastIndexOf('/') + 1);
        File nativeJar = new File(tempDir, fileName);

        // Download if not already present
        if (!nativeJar.exists()) {
            DownloadUtils.downloadFile(nativeUrl, nativeJar.getAbsolutePath(), fileName, null, listener);
        }

        // Extract the native JAR into the natives directory using zip4j
        ZipFile zipFile = new ZipFile(nativeJar);
        @SuppressWarnings("unchecked")
        List<FileHeader> fileHeaders = zipFile.getFileHeaders();
        for (FileHeader fileHeader : fileHeaders) {
            String entryName = fileHeader.getFileName();
            if (fileHeader.isDirectory() || entryName.startsWith("META-INF")) {
                continue;
            }
            File outFile = new File(nativesDir, entryName);
            outFile.getParentFile().mkdirs();
            zipFile.extractFile(fileHeader, nativesDir.getAbsolutePath());
        }
    }
}


  public boolean checkMCUpdate() {
    if (!GameUpdater.binDir.exists()) {
      Util.log("%s does not exist! Updating..", GameUpdater.binDir.getPath());
      return true;
    }
    File nativesDir = new File(binDir, "natives");
    if (!nativesDir.exists()) {
      Util.log("%s does not exist! Updating..", nativesDir.getPath());
      return true;
    }
    File minecraft = new File(binDir, "minecraft.jar");
    if (!minecraft.exists()) {
      Util.log("%s does not exist! Updating..", minecraft.getPath());
      return true;
    }

    File lib = new File(binDir, "jinput.jar");
    if (!lib.exists()) {
      Util.log("%s does not exist! Updating..", lib.getPath());
      return true;
    }

    lib = new File(binDir, "lwjgl.jar");
    if (!lib.exists()) {
      Util.log("%s does not exist! Updating..", lib.getPath());
      return true;
    }

    lib = new File(binDir, "lwjgl_util.jar");
    if (!lib.exists()) {
      Util.log("%s does not exist! Updating..", lib.getPath());
      return true;
    }

    ModpackBuild build = ModpackBuild.getSpoutcraftBuild();
    String installed = MinecraftYML.getInstalledVersion();
    String required = build.getMinecraftVersion();

    if (!installed.equals(required)) {
      Util.log("Looking for minecraft.jar version %s Found %s Updating..", required, installed);
      return true;
    }
    return false;
  }

  public void extractCompressedFile(File destinationDirectory, File compressedFile) {
    extractCompressedFile(destinationDirectory, compressedFile, false);
  }

  protected void extractCompressedFile(File destinationDirectory, File compressedFile, Boolean deleteOnSuccess) {
    try {
      Util.log("Extracting %s to %s", compressedFile.getPath(), destinationDirectory.getPath());
      if (!compressedFile.exists()) {
        Util.log("[File not Found] Cannot find %s to extract", compressedFile.getPath());
        return;
      }
      if (!destinationDirectory.exists()) {
        Util.log("Creating directory %s", destinationDirectory.getPath());
        destinationDirectory.mkdirs();
      }
      ZipFile zipFile = new ZipFile(compressedFile);
      zipFile.setRunInThread(true);
      zipFile.extractAll(destinationDirectory.getAbsolutePath());
      ProgressMonitor monitor = zipFile.getProgressMonitor();
      while (monitor.getState() == ProgressMonitor.STATE_BUSY) {
        long totalProgress = monitor.getWorkCompleted() / monitor.getTotalWork();
        stateChanged(String.format("Extracting '%s'...", monitor.getFileName()), totalProgress);
      }
      File metainfDirectory = new File(destinationDirectory, "META-INF");
      if (metainfDirectory.exists()) {
        Util.removeDirectory(metainfDirectory);
      }
      stateChanged(String.format("Extracted '%s'", compressedFile.getPath()), 100f);
      if (monitor.getResult() == ProgressMonitor.RESULT_ERROR) {
        if (monitor.getException() != null) {
          monitor.getException().printStackTrace();
        } else {
          Util.log("An error occurred without any exception while extracting %s", compressedFile.getPath());
        }
      }
      Util.log("Extracted %s to %s", compressedFile.getPath(), destinationDirectory.getPath());
    } catch (ZipException e) {
      Util.log("An error occurred while extracting %s", compressedFile.getPath());
      e.printStackTrace();
    }
  }

  public void updateSpoutcraft() throws Exception {
    performBackup();
    ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

    tempDir.mkdirs();
    workDir.mkdirs();

    File mcCache = new File(cacheDir, "minecraft_" + build.getMinecraftVersion() + ".jar");
    File updateMC = new File(tempDir.getPath() + File.separator + "minecraft.jar");
    if (mcCache.exists()) {
      copy(mcCache, updateMC);
    }

    build.install();

    // TODO: remove this once this build has been out for a few weeks
    File spoutcraftVersion = new File(GameUpdater.workDir, "versionLauncher");
    spoutcraftVersion.delete();
  }

  public boolean isSpoutcraftUpdateAvailable() {
    if (!WORKING_DIRECTORY.exists()) {
      Util.log("%s does not exist! Updating..", WORKING_DIRECTORY.getPath());
      return true;
    }
    if (!GameUpdater.workDir.exists()) {
      Util.log("%s does not exist! Updating..", GameUpdater.workDir.getPath());
      return true;
    }

    ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

    if (!build.getBuild().equalsIgnoreCase(build.getInstalledBuild())) {
      Util.log("Modpack version requested '%s' does not match installed version '%s'! Updating..", build.getBuild(), build.getInstalledBuild());
      return true;
    }
    return false;
  }

  public static long copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[1024 * 4];
    long count = 0;
    int n = 0;
    while (-1 != (n = input.read(buffer))) {
      output.write(buffer, 0, n);
      count += n;
    }
    return count;
  }

  public static void copy(File input, File output) {
    FileInputStream inputStream = null;
    FileOutputStream outputStream = null;
    try {
      inputStream = new FileInputStream(input);
      outputStream = new FileOutputStream(output);
      copy(inputStream, outputStream);
      inputStream.close();
      outputStream.close();
    } catch (Exception e) {
      Util.log("Error copying file %s to %s", input, output);
      e.printStackTrace();
    }
  }

  public void performBackup() throws IOException {
    if (!backupDir.exists()) {
      backupDir.mkdir();
    }

    String date = new StringBuilder(new SimpleDateFormat("yyyy-MM-dd-kk.mm.ss").format(new Date())).toString();
    File zip = new File(GameUpdater.backupDir, date + "-backup.zip");

    if (!zip.exists()) {
      String rootDir = modpackDir + File.separator;
      HashSet<File> exclude = new HashSet<File>();
      exclude.add(GameUpdater.backupDir);
      if (!SettingsUtil.isWorldBackup()) {
        exclude.add(GameUpdater.savesDir);
      }

      File[] existingBackups = backupDir.listFiles();
      (new BackupCleanupThread(existingBackups)).start();
      zip.createNewFile();
      stateChanged(String.format("Backing up previous build to '%s'...", zip.getName()), 0);
      addFilesToExistingZip(zip, getFiles(modpackDir, exclude, rootDir), rootDir, false);
      stateChanged(String.format("Backed up previous build to '%s'...", zip.getName()), 100);

      if (modsDir.exists())
        FileUtils.deleteDirectory(modsDir);

      if (libsDir.exists())
        FileUtils.deleteDirectory(libsDir);

      if (coremodsDir.exists())
        FileUtils.deleteDirectory(coremodsDir);

      if (modconfigsDir.exists())
        FileUtils.deleteDirectory(modconfigsDir);

      if (resourceDir.exists())
        FileUtils.deleteDirectory(resourceDir);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static boolean canPlayOffline() {
    try {
      File path = (File) AccessController.doPrivileged(new PrivilegedExceptionAction() {
        @Override
        public Object run() throws Exception {
          return WORKING_DIRECTORY;
        }
      });
      if (!path.exists()) {
        return false;
      }
      if (!new File(path, "lastlogin").exists()) {
        return false;
      }

      path = new File(path, SettingsUtil.getModPackSelection() + File.separator + "bin");
      if (!path.exists()) {
        return false;
      }
      if (!new File(path, "minecraft.jar").exists()) {
        return false;
      }
      if (!new File(path, "modpack.jar").exists()) {
        return false;
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  public static boolean canPlayOffline(String modPackName) {
    try {
      File path = AccessController.doPrivileged(new PrivilegedExceptionAction<File>() {
        @Override
        public File run() throws Exception {
          return WORKING_DIRECTORY;
        }
      });
      if (!path.exists()) {
        return false;
      }
      if (!new File(path, "lastlogin").exists()) {
        return false;
      }

      path = new File(path, modPackName + File.separator + "bin");
      if (!path.exists()) {
        return false;
      }
      if (!new File(path, "minecraft.jar").exists()) {
        return false;
      }
      if (!new File(path, "modpack.jar").exists()) {
        return false;
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  public void addFilesToExistingZip(File zipFile, Set<ClassFile> files, String rootDir, boolean progressBar) throws IOException {
    File tempFile = File.createTempFile(zipFile.getName(), null, zipFile.getParentFile());
    tempFile.delete();

    copy(zipFile, tempFile);
    boolean renameOk = zipFile.renameTo(tempFile);
    if (!renameOk) {
      if (tempFile.exists()) {
        zipFile.delete();
      } else {
        throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
      }
    }
    byte[] buf = new byte[1024];

    float progress = 0F;
    float progressStep = 0F;
    if (progressBar) {
      JarFile jarFile = new JarFile(tempFile);
      int jarSize = jarFile.size();
      jarFile.close();
      progressStep = 100F / (files.size() + jarSize);
    }

    ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(tempFile)));
    ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
    ZipEntry entry = zin.getNextEntry();
    while (entry != null) {
      String name = entry.getName();
      ClassFile entryFile = new ClassFile(name);
      if (!name.contains("META-INF") && !files.contains(entryFile)) {
        out.putNextEntry(new ZipEntry(name));
        int len;
        while ((len = zin.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
      }
      entry = zin.getNextEntry();

      progress += progressStep;
      if (progressBar) {
        stateChanged("Merging Modpack Files Into Minecraft Jar...", progress);
      }
    }
    zin.close();
    for (ClassFile file : files) {
      try {
        InputStream in = new FileInputStream(file.getFile());

        String path = file.getPath();
        path = path.replace(rootDir, "");
        path = path.replaceAll("\\\\", "/");
        out.putNextEntry(new ZipEntry(path));

        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }

        progress += progressStep;
        if (progressBar) {
          stateChanged("Merging Modpack Files Into Minecraft Jar...", progress);
        }

        out.closeEntry();
        in.close();
      } catch (IOException e) {
      }
    }

    out.close();
  }

  public Set<ClassFile> getFiles(File dir, String rootDir) {
    return getFiles(dir, new HashSet<File>(), rootDir);
  }

  public Set<ClassFile> getFiles(File dir, Set<File> exclude, String rootDir) {
    HashSet<ClassFile> result = new HashSet<ClassFile>();
    for (File file : dir.listFiles()) {
      if (!exclude.contains(dir)) {
        if (file.isDirectory()) {
          result.addAll(this.getFiles(file, exclude, rootDir));
          continue;
        }
        result.add(new ClassFile(file, rootDir));
      }
    }
    return result;
  }

  @Override
  public void stateChanged(String fileName, float progress) {
    fileName = fileName.replace(WORKING_DIRECTORY.getPath(), "");
    this.listener.stateChanged(fileName, progress);
  }

  public void setListener(DownloadListener listener) {
    this.listener = listener;
  }
}
