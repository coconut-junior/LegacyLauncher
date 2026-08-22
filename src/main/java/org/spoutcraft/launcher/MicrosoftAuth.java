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

import javax.swing.JProgressBar;

import net.technicpack.minecraftcore.mojang.auth.io.Profile;
import net.technicpack.minecraftcore.mojang.auth.response.AuthResponse;
import org.spoutcraft.launcher.exception.MCNetworkException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import com.microsoft.aad.msal4j.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MicrosoftAuth {

  private static Options options = null;

  public static Options getOptions() {
    return options;
  }

  public static void setOptions(Options options) {
    MicrosoftAuth.options = options;
  }

  public static AuthResponse doLogin(JProgressBar progress) throws IOException, MCNetworkException {
    // Try cached Minecraft auth first
    AuthResponse cached = tryLoadCachedAuth();
    if (cached != null) {
      return cached;
    }

    final String clientId = "8dfabc1d-38a9-42d8-bc08-677dbc60fe65";
    final String authority = "https://login.microsoftonline.com/consumers/";
    final String scope = "XboxLive.signin offline_access";

    PublicClientApplication pca = PublicClientApplication.builder(clientId)
        .authority(authority)
        .build();

    URI redirectUri;
    try {
        redirectUri = new URI("http://localhost");
      } catch (URISyntaxException e) {
        System.out.println("invalid redirect uri");
        throw new MCNetworkException("Invalid redirect URI: " + e.getMessage());
    }

    InteractiveRequestParameters parameters = InteractiveRequestParameters
        .builder(redirectUri)
        .scopes(Collections.singleton(scope))
        .build();

    IAuthenticationResult result;
    try {
        result = pca.acquireToken(parameters).get();
      } catch (Exception e) {
      System.out.println("auth failed");
        throw new MCNetworkException("Microsoft authentication failed: " + e.getMessage());
    }

    if (result == null || result.accessToken() == null) {
        throw new MCNetworkException("Failed to acquire Microsoft access token");
    }
    String msAccessToken = result.accessToken();

    // Step 2: Xbox Live Auth
    String xboxAuthJson = "{"
            + "\"Properties\": {"
            +     "\"AuthMethod\": \"RPS\","
            +     "\"SiteName\": \"user.auth.xboxlive.com\","
            +     "\"RpsTicket\": \"d=" + msAccessToken + "\""
            + "},"
            + "\"RelyingParty\": \"http://auth.xboxlive.com\","
            + "\"TokenType\": \"JWT\""
            + "}";
    JsonObject xboxResponse = postJson("https://user.auth.xboxlive.com/user/authenticate", xboxAuthJson);
    String xboxToken = xboxResponse.get("Token").getAsString();

    // Step 3: XSTS Auth
    String xstsJson = "{"
            + "\"Properties\": {"
            +     "\"SandboxId\": \"RETAIL\","
            +     "\"UserTokens\": [\"" + xboxToken + "\"]"
            + "},"
            + "\"RelyingParty\": \"rp://api.minecraftservices.com/\","
            + "\"TokenType\": \"JWT\""
            + "}";
    JsonObject xstsResponse = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsJson);
    String xstsToken = xstsResponse.get("Token").getAsString();
    JsonObject xstsClaims = xstsResponse.getAsJsonObject("DisplayClaims");
    String xstsUserHash = xstsClaims.getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

    // Step 4: Minecraft Auth
    String mcAuthJson = "{"
            + "\"identityToken\": \"XBL3.0 x=" + xstsUserHash + ";" + xstsToken + "\""
            + "}";
    JsonObject mcAuthResponse = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcAuthJson);
    String mcAccessToken = mcAuthResponse.get("access_token").getAsString();

    // Step 5: Minecraft Profile
    JsonObject profileResponse = getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
    String uuid = profileResponse.get("id").getAsString();
    String name = profileResponse.get("name").getAsString();

    // Build Profile object
    Profile profile = new Profile(uuid, name);

    // Build AuthResponse
    AuthResponse authResponse = new AuthResponse();

    // Use reflection to set private fields, or if fields are package-private, set directly
    // But the best way is to use a constructor if available, or extend AuthResponse with setters

    // Set accessToken and selectedProfile using reflection (if needed)
    try {
        java.lang.reflect.Field accessTokenField = AuthResponse.class.getDeclaredField("accessToken");
        accessTokenField.setAccessible(true);
        accessTokenField.set(authResponse, mcAccessToken);

        java.lang.reflect.Field selectedProfileField = AuthResponse.class.getDeclaredField("selectedProfile");
        selectedProfileField.setAccessible(true);
        selectedProfileField.set(authResponse, profile);

        java.lang.reflect.Field availableProfilesField = AuthResponse.class.getDeclaredField("availableProfiles");
        availableProfilesField.setAccessible(true);
        availableProfilesField.set(authResponse, new Profile[]{profile});
    } catch (Exception e) {
        throw new MCNetworkException("Failed to set AuthResponse fields: " + e.getMessage());
    }

    // Persist cached auth token with expiry if provided
    try {
      long expiresAt = System.currentTimeMillis() + 3600L * 1000; // default 1 hour
      if (mcAuthResponse.has("expires_in")) {
        try {
          int expiresIn = mcAuthResponse.get("expires_in").getAsInt();
          expiresAt = System.currentTimeMillis() + (expiresIn * 1000L) - 60000L; // 1 minute grace
        } catch (Exception ignored) {
        }
      }
      saveCachedAuth(mcAccessToken, expiresAt, uuid, name);
    } catch (Exception ignored) {
    }

    return authResponse;
}

  private static final Path AUTH_CACHE = new File(GameUpdater.cacheDir, "microsoft_auth.json").toPath();

  private static void saveCachedAuth(String accessToken, long expiresAt, String uuid, String name) {
    try {
      GameUpdater.cacheDir.mkdirs();
      Gson gson = new Gson();
      JsonObject obj = new JsonObject();
      obj.addProperty("access_token", accessToken);
      obj.addProperty("expires_at", expiresAt);
      obj.addProperty("uuid", uuid);
      obj.addProperty("name", name);
      String json = gson.toJson(obj);
      Files.write(AUTH_CACHE, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static AuthResponse tryLoadCachedAuth() {
    try {
      if (!Files.exists(AUTH_CACHE)) return null;
      String json = new String(Files.readAllBytes(AUTH_CACHE), StandardCharsets.UTF_8);
      JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
      String token = obj.get("access_token").getAsString();
      long expiresAt = obj.get("expires_at").getAsLong();
      String uuid = obj.get("uuid").getAsString();
      String name = obj.get("name").getAsString();
      if (System.currentTimeMillis() >= expiresAt) {
        try { Files.delete(AUTH_CACHE); } catch (Exception ignored) {}
        return null;
      }
      // Verify token still valid by fetching profile
      try {
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", token);
        // build AuthResponse
        Profile p = new Profile(uuid, name);
        AuthResponse authResponse = new AuthResponse();
        try {
            java.lang.reflect.Field accessTokenField = AuthResponse.class.getDeclaredField("accessToken");
            accessTokenField.setAccessible(true);
            accessTokenField.set(authResponse, token);

            java.lang.reflect.Field selectedProfileField = AuthResponse.class.getDeclaredField("selectedProfile");
            selectedProfileField.setAccessible(true);
            selectedProfileField.set(authResponse, p);

            java.lang.reflect.Field availableProfilesField = AuthResponse.class.getDeclaredField("availableProfiles");
            availableProfilesField.setAccessible(true);
            availableProfilesField.set(authResponse, new Profile[]{p});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return authResponse;
      } catch (Exception e) {
        try { Files.delete(AUTH_CACHE); } catch (Exception ignored) {}
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

// Helper: POST JSON and parse response
private static JsonObject postJson(String url, String json) throws IOException, MCNetworkException {
  HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    JsonParser parser = new JsonParser();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setDoOutput(true);
    try (OutputStream os = conn.getOutputStream()) {
        os.write(json.getBytes(StandardCharsets.UTF_8));
    }
    int code = conn.getResponseCode();
    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
    String response = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
            .lines().reduce("", (a, b) -> a + b);
    if (code < 200 || code >= 300) {
        throw new MCNetworkException("HTTP " + code + ": " + response);
    }
    return parser.parse(response).getAsJsonObject();
}

// Helper: GET JSON with Bearer token
private static JsonObject getJson(String url, String bearerToken) throws IOException, MCNetworkException {
  HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    JsonParser parser = new JsonParser();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
    int code = conn.getResponseCode();
    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
    String response = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
            .lines().reduce("", (a, b) -> a + b);
    if (code < 200 || code >= 300) {
        throw new MCNetworkException("HTTP " + code + ": " + response);
    }
    return parser.parse(response).getAsJsonObject();
}
}
