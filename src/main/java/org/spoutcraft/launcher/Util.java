package org.spoutcraft.launcher;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.nimbusds.jose.util.Resource;

import org.spoutcraft.launcher.modpacks.ModPackListYML;

public class Util {

  private static final String RESOURCES_PATH = "resources";

  public static final Gson GSON = new Gson();

  public static void closeQuietly(Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (IOException ex) {
      // ignore
    }
  }

  public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
    List<T> list = new ArrayList<T>(c);
    java.util.Collections.sort(list);
    return list;
  }

  public static void log(String formatString, Object... params) {
    Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(String.format(formatString, params));
  }

  public static void logi(String formatString, Object... params) {
    Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).info(String.format(formatString, params));
  }

  public static void addComboItem(JComboBox combobox, String label, String value) {
    combobox.addItem(new ComboItem(label, value));
  }

  public static void setSelectedComboByLabel(JComboBox combobox, String label) {
    for (int i = 0; i < combobox.getItemCount(); i++) {
      if (((ComboItem) combobox.getItemAt(i)).getLabel().equalsIgnoreCase(label)) {
        combobox.setSelectedIndex(i);
      }
    }
  }

  public static void setSelectedComboByValue(JComboBox combobox, String value) {
    for (int i = 0; i < combobox.getItemCount(); i++) {
      if (((ComboItem) combobox.getItemAt(i)).getValue().equalsIgnoreCase(value)) {
        combobox.setSelectedIndex(i);
      }
    }
  }

  public static String getSelectedValue(JComboBox combobox) {
    return ((ComboItem) combobox.getSelectedItem()).getValue();
  }

  public static List<String> readTextFromJar(String s) {
    InputStream is = null;
    BufferedReader br = null;
    String line;
    ArrayList<String> list = new ArrayList<String>();

    try {
      is = Main.class.getResourceAsStream(s);
      if (is == null) {
        return list;
      }
      br = new BufferedReader(new InputStreamReader(is));
      while (null != (line = br.readLine())) {
        list.add(line);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (br != null)
          br.close();
        if (is != null)
          is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return list;
  }

  public static String getBuild() {
    List<String> lines = readTextFromJar("/META-INF/maven/org.spoutcraft/technic-launcher/pom.properties");
    if (lines != null && !lines.isEmpty()) {
      for (String line : lines) {
        if (line.contains("version")) {
          return line.replace("version=", "");
        }
      }
    }

    Package launcherPackage = Main.class.getPackage();
    if (launcherPackage != null && launcherPackage.getImplementationVersion() != null) {
      return launcherPackage.getImplementationVersion();
    }

    if (Main.build != null && !Main.build.trim().isEmpty()) {
      return Main.build;
    }

    return "dev";
  }

  public static boolean removeDirectory(File directory) {
    if (directory == null)
      return false;
    if (!directory.exists())
      return true;
    if (!directory.isDirectory())
      return false;

    String[] list = directory.list();

    // Some JVMs return null for File.list() when the
    // directory is empty.
    if (list != null) {
      for (int i = 0; i < list.length; i++) {
        File entry = new File(directory, list[i]);
        if (entry.isDirectory()) {
          if (!removeDirectory(entry))
            return false;
        } else {
          if (!entry.delete())
            return false;
        }
      }
    }

    return directory.delete();
  }

  static boolean isTekkitResourceDir(File resourcesDir) {
    if (resourcesDir == null) {
      return false;
    }
    String normalizedPath = resourcesDir.getPath().replace('\\', '/');
    return normalizedPath.contains("tekkit/resources");
  }

  public static File getResourceFile(String filename) {
    return getResourceFile(filename, GameUpdater.modpackDir, ModPackListYML.currentModPackDirectory);
  }

  public static File getResourceFile(String filename, String modpack) {
    File modpackDir = new File(GameUpdater.WORKING_DIRECTORY, modpack);
    File defaultDir = new File(GameUpdater.workDir, modpack);
    return getResourceFile(filename, modpackDir, defaultDir);
  }

  public static File getResourceFile(String filename, File overridePath, File defaultPath) {
    File overridesDir = new File(overridePath, "overrides");
    File resourcesDir = new File(overridesDir, RESOURCES_PATH);
    File overrideIcon = new File(resourcesDir, filename);
    if (overrideIcon.exists())
      return overrideIcon;
    resourcesDir = new File(defaultPath, RESOURCES_PATH);

    overrideIcon = new File(resourcesDir, filename);
    if (overrideIcon.exists()) {
      return overrideIcon;
    }

    File bundledResource = copyBundledResource(defaultPath, filename);
    if (bundledResource != null) {
      return bundledResource;
    }

    return overrideIcon;
  }

  public static File copyBundledResource(File modpackDir, String filename) {
    if (modpackDir == null || modpackDir.getName() == null || modpackDir.getName().trim().isEmpty()) {
      return null;
    }

    String modpackName = modpackDir.getName();
    String[] candidates = new String[] {
        "/org/spoutcraft/launcher/" + modpackName + "/resources/" + filename,
        "/org/spoutcraft/launcher/" + modpackName + "/" + filename,
        "/org/spoutcraft/launcher/modpacks/" + modpackName + "/resources/" + filename,
        "/modpacks/" + modpackName + "/resources/" + filename,
        "/org/spoutcraft/launcher/resources/" + modpackName + "/" + filename
    };

    for (String candidate : candidates) {
      try (InputStream input = Main.class.getResourceAsStream(candidate)) {
        if (input == null) {
          continue;
        }
        File targetDir = new File(modpackDir, RESOURCES_PATH);
        targetDir.mkdirs();
        File targetFile = new File(targetDir, filename);
        try (OutputStream output = new FileOutputStream(targetFile)) {
          byte[] buffer = new byte[4096];
          int read;
          while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
          }
        }
        return targetFile;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return null;
  }
}