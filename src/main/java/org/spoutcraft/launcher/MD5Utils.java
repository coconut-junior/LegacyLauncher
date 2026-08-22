package org.spoutcraft.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.util.config.Configuration;

public class MD5Utils {

  private static final String              CHECKSUM_MD5  = "CHECKSUM.md5";
  private static final File                CHECKSUM_FILE = new File(GameUpdater.workDir, CHECKSUM_MD5);
  private static boolean                   updated;
  private static final Map<String, String> md5Map        = new HashMap<String, String>();

  // In-memory cache for MD5 hashes: key is file path + lastModified + length
  private static final Map<String, String> md5FileCache = new HashMap<>();
  private static final File MD5_CACHE_FILE = new File(GameUpdater.cacheDir, "md5cache.ser");
  private static final Object cacheLock = new Object();

  static {
    // Ensure cache directory exists
    try {
      GameUpdater.cacheDir.mkdirs();
    } catch (Exception ignored) {
    }
    // Load persisted md5 cache if present
    loadMd5Cache();
    // Save cache on JVM shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> saveMd5Cache()));
  }

  public static String getMD5(File file) {
    if (file == null || !file.exists()) {
      return null;
    }
    String cacheKey = file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
    String cached;
    synchronized (cacheLock) {
      cached = md5FileCache.get(cacheKey);
    }
    if (cached != null) {
      return cached;
    }
    FileInputStream stream = null;
    try {
      stream = new FileInputStream(file);
      String md5Hex = DigestUtils.md5Hex(stream);
      stream.close();
      synchronized (cacheLock) {
        md5FileCache.put(cacheKey, md5Hex);
      }
      // Persist asynchronously to avoid blocking
      saveMd5CacheAsync();
      return md5Hex;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }

  private static void loadMd5Cache() {
    if (!MD5_CACHE_FILE.exists()) {
      return;
    }
    synchronized (cacheLock) {
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(MD5_CACHE_FILE))) {
        Object obj = ois.readObject();
        if (obj instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, String> map = (Map<String, String>) obj;
          md5FileCache.clear();
          md5FileCache.putAll(map);
        }
      } catch (Exception e) {
        // Corrupt cache - ignore
        try {
          MD5_CACHE_FILE.delete();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static void saveMd5CacheAsync() {
    new Thread(() -> saveMd5Cache(), "MD5Cache-Saver").start();
  }

  private static void saveMd5Cache() {
    synchronized (cacheLock) {
      try {
        File tmp = new File(MD5_CACHE_FILE.getParentFile(), MD5_CACHE_FILE.getName() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp))) {
          oos.writeObject(md5FileCache);
        }
        GameUpdater.copy(tmp, MD5_CACHE_FILE);
        tmp.delete();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static String getMD5(FileType type) {
    return getMD5(type, MinecraftYML.getLatestMinecraftVersion());
  }

  @SuppressWarnings("unchecked")
  public static String getMD5(FileType type, String version) {
    Configuration config = MinecraftYML.getMinecraftYML();
    Map<String, Map<String, String>> builds = (Map<String, Map<String, String>>) config.getProperty("versions");
    if (builds.containsKey(version)) {
      Map<String, String> files = builds.get(version);
      return files.get(type.name());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static String getMinecraftMD5(String md5Hash) {
    Configuration config = MinecraftYML.getMinecraftYML();
    Map<String, Map<String, String>> builds = (Map<String, Map<String, String>>) config.getProperty("versions");
    for (String version : builds.keySet()) {
      Util.log("Checking version "+version);
      String minecraftMD5 = builds.get(version).get("minecraft");
      if (minecraftMD5.equalsIgnoreCase(md5Hash)) {
        return version;
      }
    }
    return null;
  }

  public static void updateMD5Cache() {
    if (!updated && !Main.isOffline) {
      updated = true;
      try {
        String urlStr = MirrorUtils.getMirrorUrl(CHECKSUM_MD5, null);

        if (urlStr == null) {
          if (GameUpdater.canPlayOffline()) {
            Main.isOffline = true;
            parseChecksumFile();
          }
          return;
        }

        // Try conditional GET using If-Modified-Since to avoid re-downloading unchanged checksum file
        URL url = new URL(urlStr);
        try {
          HttpURLConnection http = (HttpURLConnection) url.openConnection();
          if (CHECKSUM_FILE.exists()) {
            http.setIfModifiedSince(CHECKSUM_FILE.lastModified());
          }
          http.setRequestProperty("User-Agent", "Technic-Launcher/1.0");
          http.setConnectTimeout(5000);
          http.setReadTimeout(10000);
          http.connect();
          int code = http.getResponseCode();
          if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            // No change; parse existing file
            parseChecksumFile();
          } else if (code == HttpURLConnection.HTTP_OK) {
            // Download new file to temp then move into place
            InputStream in = new BufferedInputStream(http.getInputStream());
            File tmp = File.createTempFile("checksum", null, GameUpdater.tempDir);
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
              byte[] buf = new byte[8192];
              int r;
              while ((r = in.read(buf)) != -1) {
                fos.write(buf, 0, r);
              }
            } finally {
              try {
                in.close();
              } catch (IOException ignored) {
              }
            }
            GameUpdater.copy(tmp, CHECKSUM_FILE);
            tmp.delete();
            parseChecksumFile();
          } else {
            // Fallback to existing behavior
            if (DownloadUtils.downloadFile(urlStr, CHECKSUM_FILE.getPath()).isSuccess()) {
              parseChecksumFile();
            }
          }
        } catch (ClassCastException e) {
          // Non-HTTP URL, fallback to DownloadUtils
          if (DownloadUtils.downloadFile(urlStr, CHECKSUM_FILE.getPath()).isSuccess()) {
            parseChecksumFile();
          }
        }
      } catch (FileNotFoundException e) {
        Util.log("Checksum file '%s' not found.", CHECKSUM_FILE.getAbsoluteFile());
        e.printStackTrace();
      } catch (IOException e) {
        Util.log("Checksum file '%s' threw error.", CHECKSUM_FILE.getAbsoluteFile());
        e.printStackTrace();
      }
    }
  }

  private static void parseChecksumFile() throws FileNotFoundException {
    md5Map.clear();
    Scanner scanner = new Scanner(CHECKSUM_FILE);
    Scanner scannerDelimited = scanner.useDelimiter("\\||\n");
    while (scannerDelimited.hasNext()) {
      String md5 = scannerDelimited.next().toLowerCase();
      String path = scannerDelimited.next().replace("\r", "").replace('/', '\\');
      md5Map.put(path, md5);
      scannerDelimited.nextLine();
    }
    scanner.close();
  }

  public static boolean checksumPath(String relativePath) {
    return checksumPath(relativePath, relativePath);
  }

  public static boolean checksumPath(String filePath, String md5Path) {
    return checksumPath(new File(GameUpdater.workDir, filePath), md5Path);
  }

  public static boolean checksumCachePath(String filePath, String md5Path) {
    return checksumPath(new File(GameUpdater.cacheDir, filePath), md5Path);
  }

  public static boolean checksumPath(File file, String md5Path) {
    if (!file.exists()) {
      return false;
    }
    // Skip MD5 check for .png files if not empty
    if (file.getName().toLowerCase().endsWith(".png") && file.length() > 0) {
      return true;
    }
    String fileMD5 = getMD5(file);
    String storedMD5 = getMD5FromList(md5Path);
    if (storedMD5 == null) {
      Util.log("MD5 hash not found for '%s'", md5Path);
    }
    boolean doesMD5Match = (storedMD5 == null) ? false : storedMD5.equalsIgnoreCase(fileMD5);
    if (!doesMD5Match) {
      Util.log("[MD5 Mismatch] File '%s' has md5 of '%s' instead of '%s'", file, fileMD5, storedMD5);
    }
    return doesMD5Match;
  }

  public static String getMD5FromList(String md5Path) {
    md5Path = md5Path.replace('/', '\\');
    return (!md5Map.containsKey(md5Path)) ? null : md5Map.get(md5Path);
  }
}
