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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

import javax.swing.JProgressBar;

import com.google.gson.Gson;
import net.technicpack.minecraftcore.mojang.auth.request.AuthRequest;
import net.technicpack.minecraftcore.mojang.auth.response.AuthResponse;
import org.spoutcraft.launcher.exception.AccountMigratedException;
import org.spoutcraft.launcher.exception.BadLoginException;
import org.spoutcraft.launcher.exception.MCNetworkException;
import org.spoutcraft.launcher.exception.MinecraftUserNotPremiumException;
import org.spoutcraft.launcher.exception.OutdatedMCLauncherException;

public class MinecraftUtils {

  private static Options options = null;

  public static Options getOptions() {
    return options;
  }

  public static void setOptions(Options options) {
    MinecraftUtils.options = options;
  }

  public static AuthResponse doLogin(String user, String pass, JProgressBar progress) throws BadLoginException, MCNetworkException, OutdatedMCLauncherException,
          IOException, MinecraftUserNotPremiumException {
    AuthRequest request = new AuthRequest(user, pass, UUID.randomUUID().toString());
    String parameters = Util.GSON.toJson(request);
    String response = PlatformUtils.excutePost("https://authserver.mojang.com/authenticate", parameters, progress);
    AuthResponse result = Util.GSON.fromJson(response, AuthResponse.class);
    if (result == null) {
      throw new MCNetworkException();
    }
    return result;
  }
}
