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
package org.spoutcraft.launcher.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.awt.Image;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import net.technicpack.minecraftcore.mojang.auth.response.AuthResponse;
import org.spoutcraft.launcher.GameUpdater;
import org.spoutcraft.launcher.LibrariesYML;
import org.spoutcraft.launcher.MD5Utils;
import org.spoutcraft.launcher.Main;
import org.spoutcraft.launcher.MicrosoftAuth;
import org.spoutcraft.launcher.MinecraftYML;
import org.spoutcraft.launcher.MirrorUtils;
import org.spoutcraft.launcher.PlatformUtils;
import org.spoutcraft.launcher.SettingsUtil;
import org.spoutcraft.launcher.SpoutFocusTraversalPolicy;
import org.spoutcraft.launcher.Util;
import org.spoutcraft.launcher.async.DownloadListener;
import org.spoutcraft.launcher.exception.NoMirrorsAvailableException;
import org.spoutcraft.launcher.gui.widget.ComboBoxRenderer;
import org.spoutcraft.launcher.modpacks.ModLibraryYML;
import org.spoutcraft.launcher.modpacks.ModPackListYML;
import org.spoutcraft.launcher.modpacks.ModPackUpdater;
import org.spoutcraft.launcher.modpacks.ModPackYML;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class LoginForm extends JFrame implements ActionListener, DownloadListener, KeyListener, WindowListener {

  private static final long                serialVersionUID = 1L;
  private final BackgroundPanel            contentPane;
  private final JPasswordField             passwordField;
  private final JComboBox                  usernameField    = new JComboBox();
  private final JButton                    loginButton      = new JButton("Login");
  JButton                                  optionsButton    = new JButton("Options");
  JButton                                  modsButton       = new JButton("Mod Select");
  private final JCheckBox                  rememberCheckbox = new JCheckBox("Remember");
  private final JButton                    offlineMode      = new JButton("Offline Mode");
  private final JButton                    tryAgain         = new JButton("Try Again");
  final JTextPane                          editorPane       = new JTextPane();
  private final JAccountButton             loginSkin1;
  private final List<JButton>              loginSkin1Image;
  private final JAccountButton             loginSkin2;
  private final List<JButton>              loginSkin2Image;
  private TumblerFeedParsingWorker         tumblerFeed;
  public final JProgressBar                progressBar;
  HashMap<String, UserPasswordInformation> usernames        = new HashMap<String, UserPasswordInformation>();
  public boolean                           mcUpdate         = false;
  public boolean                           spoutUpdate      = false;
  public boolean                           modpackUpdate    = false;
  public static UpdateDialog               updateDialog;
  private static String                    pass             = null;
  public static AuthResponse               values           = null;
  private int                              success          = LauncherFrame.ERROR_IN_LAUNCH;
  public String                            workingDir       = PlatformUtils.getWorkingDirectory().getAbsolutePath();
  public static final ModPackUpdater       gameUpdater      = new ModPackUpdater();
  OptionDialog                             options          = null;
  ModsDialog                               mods             = new ModsDialog(ModPackYML.getModList());
  Container                                loginPane        = new Container();
  Container                                offlinePane      = new Container();
  private final JComboBox                  modpackList;
  private boolean                           controlsInitialized;
  private boolean                           brandingLoaded;

  public LoginForm() {
    loadLauncherData();

    LoginForm.updateDialog = new UpdateDialog(this);
    gameUpdater.setListener(this);

    this.addWindowListener(this);

    loginButton.setFont(new Font("Arial", Font.PLAIN, 11));
    loginButton.setBounds(272, 13, 86, 23);
    loginButton.setOpaque(false);
    loginButton.addActionListener(this);
    loginButton.setEnabled(false); // disable until login info is read
    optionsButton.setFont(new Font("Arial", Font.PLAIN, 11));
    optionsButton.setOpaque(false);
    optionsButton.addActionListener(this);
    optionsButton.setEnabled(false);
    modsButton.setFont(new Font("Arial", Font.PLAIN, 11));
    modsButton.setOpaque(false);
    modsButton.addActionListener(this);
    usernameField.setFont(new Font("Arial", Font.PLAIN, 11));
    usernameField.addActionListener(this);
    usernameField.setOpaque(false);
    usernameField.setEditable(false);
    offlineMode.setFont(new Font("Arial", Font.PLAIN, 11));
    offlineMode.setOpaque(false);
    offlineMode.addActionListener(this);

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    setBounds((dim.width - 860) / 2, (dim.height - 500) / 2, 860, 500);

    contentPane = new BackgroundPanel();
    contentPane.setOpaque(true);
    contentPane.setBackground(new Color(32, 32, 32));
    contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    setContentPane(contentPane);

    // lblLogo = new JLabel("");
    // lblLogo.setBounds(8, 0, 294, 99);
    List<String> items = new ArrayList<String>();
    int i = 0;
    for (String item : ModPackListYML.modpackMap.keySet()) {
      if (!Main.isOffline || GameUpdater.canPlayOffline(item)) {
        items.add(item);
        i += 1;
      }
    }
    String[] itemArray = new String[i];
    modpackList = new JComboBox(items.toArray(itemArray));
    modpackList.setBounds(10, 10, 328, 100);
    modpackList.setOpaque(false);
    modpackList.setBackground(new Color(255, 255, 255));
    modpackList.setForeground(Color.DARK_GRAY);
    ComboBoxRenderer renderer = new ComboBoxRenderer();
    renderer.setPreferredSize(new Dimension(200, 110));

    modpackList.setRenderer(renderer);
    modpackList.setMaximumRowCount(4);
    modpackList.setSelectedItem(SettingsUtil.getModPackSelection());
    modpackList.addActionListener(this);
    // Ensure background is repainted when the combo popup closes to avoid painting artifacts
    modpackList.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        contentPane.revalidate();
        contentPane.repaint();
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        contentPane.revalidate();
        contentPane.repaint();
      }
    });

    JLabel lblMinecraftUsername = new JLabel("Microsoft Email: ");
    lblMinecraftUsername.setFont(new Font("Arial", Font.PLAIN, 11));
    lblMinecraftUsername.setHorizontalAlignment(SwingConstants.RIGHT);
    lblMinecraftUsername.setBounds(-17, 17, 150, 14);

    JLabel lblPassword = new JLabel("Password: ");
    lblPassword.setFont(new Font("Arial", Font.PLAIN, 11));
    lblPassword.setHorizontalAlignment(SwingConstants.RIGHT);
    lblPassword.setBounds(33, 42, 100, 20);

    passwordField = new JPasswordField();
    passwordField.setFont(new Font("Arial", Font.PLAIN, 11));
    passwordField.setBounds(143, 42, 119, 22);
    passwordField.setText("password");
    passwordField.setEditable(false);

    loginSkin1 = new JAccountButton("Login as Player");
    loginSkin1.setFont(new Font("Arial", Font.PLAIN, 11));
    loginSkin1.setBounds(72, 428, 119, 23);
    loginSkin1.setOpaque(false);
    loginSkin1.addActionListener(this);
    loginSkin1.setVisible(false);
    loginSkin1Image = new ArrayList<JButton>();

    loginSkin2 = new JAccountButton("Login as Player");
    loginSkin2.setFont(new Font("Arial", Font.PLAIN, 11));
    loginSkin2.setBounds(261, 428, 119, 23);
    loginSkin2.setOpaque(false);
    loginSkin2.addActionListener(this);
    loginSkin2.setVisible(false);
    loginSkin2Image = new ArrayList<JButton>();

    progressBar = new JProgressBar();
    progressBar.setBounds(30, 120, 400, 23);
    progressBar.setVisible(false);
    progressBar.setStringPainted(true);
    progressBar.setOpaque(true);
    progressBar.setBackground(new Color(235, 235, 235));

    JLabel purchaseAccount = new HyperlinkJLabel("<html><u>Need a minecraft account?</u></html>", "http://www.minecraft.net/register.jsp");
    purchaseAccount.setHorizontalAlignment(SwingConstants.RIGHT);
    purchaseAccount.setBounds(243, 70, 111, 14);

    purchaseAccount.setText("<html><u>Need an account?</u></html>");
    purchaseAccount.setFont(new Font("Arial", Font.PLAIN, 11));
    purchaseAccount.setForeground(new Color(0, 0, 255));

    JLabel wikiLink = new HyperlinkJLabel("<html><u>Technic WebSite</u></html>", "http://technicpack.net/");
    wikiLink.setHorizontalAlignment(SwingConstants.RIGHT);
    wikiLink.setBounds(233, 85, 109, 14);

    // wikiLink.setText();
    wikiLink.setFont(new Font("Arial", Font.PLAIN, 11));
    wikiLink.setForeground(new Color(0, 0, 255));

    usernameField.setBounds(143, 14, 119, 25);
    rememberCheckbox.setFont(new Font("Arial", Font.PLAIN, 11));

    rememberCheckbox.setOpaque(false);


    editorPane.setContentType("text/html");

    readUsedUsernames();

    editorPane.setEditable(false);
    editorPane.setOpaque(true);
    editorPane.setBackground(new Color(255, 255, 255));
    // editorPane.setBorder(null);
    // editorPane.setMargin(new Insets(0,0,0,0));
    editorPane.setFocusable(false);



    JScrollPane scrollPane = new JScrollPane(editorPane);
    scrollPane.setBounds(473, 11, 372, 340);
    scrollPane.setBorder(null);
    // Ensure the scroll pane and its viewport are opaque so the editorPane background is visible
    scrollPane.setOpaque(true);
    scrollPane.getViewport().setOpaque(true);
    scrollPane.getViewport().setBorder(null);

    editorPane.setCaretPosition(0);


    JLabel login = new JLabel();
    login.setBackground(new Color(255, 255, 255, 120));
    login.setOpaque(true);
    login.setBounds(473, 362, 372, 99);

    JLabel trans;
    trans = new JLabel();
    trans.setBackground(new Color(0, 0, 0, 0));
    trans.setOpaque(false);
    trans.setBounds(0, 0, 854, 480);

    usernameField.getEditor().addActionListener(this);
    passwordField.addKeyListener(this);
    rememberCheckbox.addKeyListener(this);

    contentPane.setLayout(null);
    rememberCheckbox.setBounds(144, 66, 93, 23);
    rememberCheckbox.setSelected(true);
    // contentPane.add(lblLogo);
    contentPane.add(modpackList);
    optionsButton.setBounds(272, 41, 86, 23);
    modsButton.setBounds(15, 66, 93, 23);
    contentPane.add(loginSkin1);
    contentPane.add(loginSkin2);

    loginPane.setBounds(473, 362, 372, 106);
    loginPane.add(lblPassword);
    loginPane.add(lblMinecraftUsername);
    loginPane.add(passwordField);
    loginPane.add(usernameField);
    loginPane.add(loginButton);
    loginPane.add(rememberCheckbox);
    loginPane.add(purchaseAccount);
    loginPane.add(wikiLink);
    loginPane.add(optionsButton);
    loginPane.add(modsButton);
    contentPane.add(loginPane);

    JLabel offlineMessage = new JLabel("Could not connect to minecraft.net");
    offlineMessage.setFont(new Font("Arial", Font.PLAIN, 14));
    offlineMessage.setBounds(25, 40, 217, 17);

    tryAgain.setOpaque(false);
    tryAgain.setFont(new Font("Arial", Font.PLAIN, 12));
    tryAgain.setBounds(257, 20, 100, 25);

    offlineMode.setOpaque(false);
    offlineMode.setFont(new Font("Arial", Font.PLAIN, 12));
    offlineMode.setBounds(257, 52, 100, 25);

    offlinePane.setBounds(473, 362, 372, 99);
    // offlinePane.add(tryAgain);
    offlinePane.add(offlineMode);
    offlinePane.add(offlineMessage);
    offlinePane.setVisible(false);
    contentPane.add(offlinePane);

    contentPane.add(scrollPane);
    contentPane.add(login);
    contentPane.add(trans);
    contentPane.add(progressBar);

    // TODO: remove this after next release
    (new File(PlatformUtils.getWorkingDirectory(), "launcher_cache.jpg")).delete();

    List<Component> order = new ArrayList<Component>(6);
    order.add(usernameField.getEditor().getEditorComponent());
    order.add(passwordField);
    order.add(rememberCheckbox);
    order.add(loginButton);
    order.add(optionsButton);
    order.add(modsButton);

    setFocusTraversalPolicy(new SpoutFocusTraversalPolicy(order));

    // loginButton.setEnabled(true); // enable once logins are read
    modsButton.setEnabled(false);
    setResizable(false);

    if (Main.isOffline) {
      offlinePane.setVisible(true);
      loginPane.setVisible(false);
    }

    usernameField.setEnabled(!Main.isOffline);
    passwordField.setEnabled(!Main.isOffline);
    controlsInitialized = true;
    enableLoginControls();
  }

  private void enableLoginControls() {
    if (controlsInitialized && brandingLoaded && !Main.isOffline) {
      loginButton.setEnabled(true);
      optionsButton.setEnabled(true);
      usernameField.setEnabled(true);
      passwordField.setEnabled(true);
    }
  }

  public void loadLauncherData() {
    if (!Boolean.getBoolean("launcher.skipSplash")) {
      System.out.println("[Startup] Loading launcher metadata...");
      boolean useBundledModpacks = ModPackListYML.hasBundledModpacksYml();
      System.out.println("[Startup] Bundled modpacks.yml present: " + useBundledModpacks);
      if (!useBundledModpacks) {
        System.out.println("[Startup] Fetching mirrors.yml...");
        MirrorUtils.updateMirrorsYMLCache();
        System.out.println("[Startup] mirrors.yml complete");
        System.out.println("[Startup] Updating MD5 cache...");
        MD5Utils.updateMD5Cache();
        System.out.println("[Startup] MD5 cache complete");
        System.out.println("[Startup] Fetching modpacks.yml...");
        ModPackListYML.updateModPacksYMLCache();
        System.out.println("[Startup] modpacks.yml complete");
        System.out.println("[Startup] Downloading modpack resources...");
        ModPackListYML.getAllModPackResources();
        System.out.println("[Startup] modpack resources complete");
        System.out.println("[Startup] Loading libraries.yml...");
        LibrariesYML.updateLibrariesYMLCache();
        System.out.println("[Startup] libraries.yml complete");
        System.out.println("[Startup] Loading modlibrary.yml...");
        ModLibraryYML.updateModLibraryYML();
        System.out.println("[Startup] modlibrary.yml complete");
      } else {
        System.out.println("[Startup] Using bundled modpacks.yml; skipping remote metadata refresh");
        ModPackListYML.updateModPacksYMLCache();
        System.out.println("[Startup] bundled modpacks.yml loaded");
      }
    }

    System.out.println("[Startup] Loading modpack logos...");
    ModPackListYML.loadModpackLogos();
    System.out.println("[Startup] Modpack logos loaded");

    if (SettingsUtil.getModPackSelection() != null) {
      updateBranding();
    } else {
      setTitle("Technic Launcher - No Modpack Selected");
    }

    if (!Main.isOffline && GameUpdater.canPlayOffline()
        && !MirrorUtils.isNetworkAvailable("https://login.microsoftonline.com/consumers/")) {
      Main.isOffline = true;
    }
  }

  public void updateBranding() {
    loginButton.setEnabled(false);
    optionsButton.setEnabled(false);
    setTitle("Loading Modpack Data...");
    SwingWorker<Object, Object> updateThread = new SwingWorker<Object, Object>() {

      @Override
      protected Object doInBackground() throws Exception {
        ModPackListYML.setCurrentModpack();
        return null;
      }

      @Override
      protected void done() {
        brandingLoaded = true;
        if (options == null) {
          options = new OptionDialog();
          options.modPackList = ModPackListYML.modpackMap;
          options.setVisible(false);
        }

        enableLoginControls();
        setIconImage(Toolkit.getDefaultToolkit().getImage(ModPackYML.getModPackIcon()));
        setTitle(String.format("Technic Launcher - %s - (%s)", Main.build, ModPackListYML.currentModPackLabel));
        options.reloadSettings();
        MinecraftYML.updateMinecraftYMLCache();
        setModLoaderEnabled();
      }
    };
    updateThread.execute();
  }

  public void setModLoaderEnabled() {
    File modLoaderConfig = new File(GameUpdater.modconfigsDir, "ModLoader.cfg");
    boolean modLoaderExists = modLoaderConfig.exists();
    modsButton.setEnabled(modLoaderExists);
  }

  @Override
  public void stateChanged(String fileName, float progress) {
    int intProgress = Math.round(progress);

    if (intProgress >= 0) {
      progressBar.setValue(intProgress);
      progressBar.setIndeterminate(false);
    } else {
      progressBar.setIndeterminate(true);
    }

    fileName = fileName.replace(workingDir, "");
    if (fileName.contains("?")) {
      fileName = fileName.substring(0, fileName.indexOf("?"));
    }

    if (fileName.length() > 60) {
      fileName = fileName.substring(0, 60) + "...";
    }
    String progressText = intProgress + "% " + fileName;
    if (intProgress < 0)
      progressText = fileName;
    progressBar.setString(progressText);
  }

  @Override
  public void keyTyped(KeyEvent e) {
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (loginButton.isEnabled() && e.getKeyCode() == KeyEvent.VK_ENTER) {
      doLogin();
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {
  }

  private void readUsedUsernames() {
    int i = 0;
    try {
      File lastLogin = new File(PlatformUtils.getWorkingDirectory(), "lastlogin");
      if (!lastLogin.exists()) {
        return;
      }
      Cipher cipher = getCipher(2, "passwordfile");

      DataInputStream dis;
      if (cipher != null) {
        dis = new DataInputStream(new CipherInputStream(new FileInputStream(lastLogin), cipher));
      } else {
        dis = new DataInputStream(new FileInputStream(lastLogin));
      }

      try {
        // noinspection InfiniteLoopStatement
        while (true) {
          String user = dis.readUTF();
          boolean isHash = dis.readBoolean();
          if (isHash) {
            byte[] hash = new byte[32];
            dis.read(hash);

            usernames.put(user, new UserPasswordInformation(hash));
          } else {
            String password = dis.readUTF();
            if (!password.isEmpty()) {
              i++;
              String skinName = user;
              if (dis.readBoolean())
                skinName = dis.readUTF();

              String skin = PlatformUtils.getUserSkin(skinName);
              if (i == 1) {
                // if (tumblerFeed != null) {
                TumblerFeedParsingWorker.setUser(skinName);
                // }
                if (!Main.isOffline) {
                  loginSkin1.setText(skinName);
                  loginSkin1.setAccountName(user);
                  loginSkin1.setVisible(true);
                  ImageUtils.drawCharacter(contentPane, this, skin, 103, 170, loginSkin1Image);
                }
              } else if (i == 2) {
                if (!Main.isOffline) {
                  loginSkin2.setText(skinName);
                  loginSkin2.setAccountName(user);
                  loginSkin2.setVisible(true);
                  ImageUtils.drawCharacter(contentPane, this, skin, 293, 170, loginSkin2Image);
                }
              }
            }
            usernames.put(user, new UserPasswordInformation(password));
          }
          this.usernameField.addItem(user);
          Util.log("Setting username to " + user);
        }
      } catch (EOFException ignored) {
      }
      dis.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
    updatePasswordField();
  }

  private void writeUsernameList() {
    try {
      File lastLogin = new File(PlatformUtils.getWorkingDirectory(), "lastlogin");

      Cipher cipher = getCipher(1, "passwordfile");

      DataOutputStream dos;
      if (cipher != null) {
        dos = new DataOutputStream(new CipherOutputStream(new FileOutputStream(lastLogin), cipher));
      } else {
        dos = new DataOutputStream(new FileOutputStream(lastLogin, true));
      }
      for (String user : usernames.keySet()) {
        dos.writeUTF(user);
        UserPasswordInformation info = usernames.get(user);
        dos.writeBoolean(info.isHash);
        if (info.isHash) {
          dos.write(info.passwordHash);
        } else {
          dos.writeUTF(info.password);
        }
        dos.writeBoolean(info.hasProfileName());
        dos.writeUTF(info.getProfileName());
      }
      dos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    String eventId = event.getActionCommand();
    Object source = event.getSource();
    if (source == loginSkin1 || source == loginSkin2) {
      eventId = "Login";
      this.usernameField.setSelectedItem(((JAccountButton) source).getAccountEmail());
    } else if (loginSkin1Image.contains(source)) {
      eventId = "Login";
      this.usernameField.setSelectedItem(loginSkin1.getAccountEmail());
    } else if (loginSkin2Image.contains(source)) {
      eventId = "Login";
      this.usernameField.setSelectedItem(loginSkin2.getAccountEmail());
    }
    if ((source == modpackList)) {
      if (ModPackListYML.currentModPack == null) {
        SettingsUtil.init();
        GameUpdater.copy(SettingsUtil.settingsFile, ModPackListYML.ORIGINAL_PROPERTIES);
      } else {
        GameUpdater.copy(SettingsUtil.settingsFile, new File(GameUpdater.modpackDir, "launcher.properties"));
      }
      String selectedItem = (String) modpackList.getSelectedItem();
      SettingsUtil.setModPack(selectedItem);
      updateBranding();
    }
    if ((eventId.equals("Login") || eventId.equals(usernameField.getSelectedItem())) && loginButton.isEnabled()) {
      doLogin();
    } else if (eventId.equals("Options")) {
      options.setVisible(true);
      options.setBounds((int) getBounds().getCenterX() - 250, (int) getBounds().getCenterY() - 75, 360, 325);
    } else if (eventId.equals(modsButton.getText())) {
      if (ModPackListYML.currentModPack != null) {
        open(new File(GameUpdater.modconfigsDir, "ModLoader.cfg"));
      }
    } else if (eventId.equals("comboBoxChanged")) {
      updatePasswordField();
    }

    if (source == offlineMode) {
      Main.isOffline = true;
      gameUpdater.user = "user";
      gameUpdater.downloadTicket = "0";
      offlineMode.setEnabled(false);
      tryAgain.setEnabled(false);
      runGame();
    }
  }

  public static void open(File document) {
    if (!document.exists())
      return;
    try {
      Desktop dt = Desktop.getDesktop();
      dt.open(document);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void updatePasswordField() {
    if (this.usernameField.getSelectedItem() != null) {
      UserPasswordInformation info = usernames.get(this.usernameField.getSelectedItem().toString());
      if (info != null) {
        if (info.isHash) {
          this.passwordField.setText("");
          this.rememberCheckbox.setSelected(false);
        } else {
          this.passwordField.setText(info.password);
          this.rememberCheckbox.setSelected(true);
        }
      }
    }
  }

  public void doLogin() {
    this.loginButton.setEnabled(false);
    this.optionsButton.setEnabled(false);
    this.modsButton.setEnabled(false);
    this.loginSkin1.setEnabled(false);
    this.loginSkin2.setEnabled(false);
    options.setVisible(false);
    SwingWorker<Boolean, Boolean> loginThread = new SwingWorker<Boolean, Boolean>() {

      @Override
      protected Boolean doInBackground() {
        progressBar.setVisible(true);
        progressBar.setString("Connecting to www.minecraft.net...");
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        
        try {
          System.out.println("Attempting login...");
          values = MicrosoftAuth.doLogin(progressBar);
          StringSelection stringSelection = new StringSelection(values.toString());
          clipboard.setContents(stringSelection, null);
          return true;
        } catch (Exception e) {
          this.cancel(true);
          progressBar.setString(e.getMessage());
          System.out.println(e.getMessage());

          if (GameUpdater.canPlayOffline()) {
            Main.isOffline = true;
            SwingUtilities.invokeLater(() -> {
              loginPane.setVisible(false);
              offlinePane.setVisible(true);
            });
          }

          //copy error
          String myString = e.getMessage();
          StringSelection stringSelection = new StringSelection(myString);
          clipboard.setContents(stringSelection, null);
        }

        enableUI();
        this.cancel(true);
        return false;
      }

      @Override
      protected void done() {
        if (values == null || values.getError() != null) {
          return;
        }

        LoginForm.pass = "password";
        String profileName = values.getSelectedProfile().getName();
        usernames.put(profileName, new UserPasswordInformation("password"));
        writeUsernameList();
        readUsedUsernames();

        MessageDigest digest = null;

        try {
          digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
        }

        gameUpdater.user = usernameField.getSelectedItem().toString(); // values[2].trim();
        gameUpdater.downloadTicket = "1";

        SwingWorker<Boolean, String> updateThread = new SwingWorker<Boolean, String>() {

          @Override
          protected Boolean doInBackground() throws Exception {
            publish("Checking for Minecraft Update...\n");
            try {
              mcUpdate = gameUpdater.checkMCUpdate();
            } catch (Exception e) {
              e.printStackTrace();
            }

            publish("Checking for Spoutcraft update...\n");
            try {
              spoutUpdate = gameUpdater.isSpoutcraftUpdateAvailable();
            } catch (Exception e) {
              e.printStackTrace();
            }

            publish(String.format("Checking for %s update...\n", ModPackListYML.currentModPackLabel));
            try {
              modpackUpdate = gameUpdater.isModpackUpdateAvailable();
            } catch (Exception e) {
              e.printStackTrace();
            }
            return true;
          }

          @Override
          protected void done() {
            if (modpackUpdate) {
              updateDialog.setToUpdate(ModPackListYML.currentModPackLabel);
            } else if (spoutUpdate) {
              updateDialog.setToUpdate("Spoutcraft");
            } else if (mcUpdate) {
              updateDialog.setToUpdate("Minecraft");
            }
            if (mcUpdate || spoutUpdate || modpackUpdate) {
              if (!GameUpdater.binDir.exists() || mcUpdate) {
                updateThread();
              } else {
                LoginForm.updateDialog.setVisible(true);
              }
            } else {
              runGame();
            }
            this.cancel(true);
          }

          @Override
          protected void process(List<String> chunks) {
            progressBar.setString(chunks.get(0));
          }
        };
        updateThread.execute();
        this.cancel(true);
      }
    };
    loginThread.execute();
  }

  public void updateThread() {
    SwingWorker<Boolean, String> updateThread = new SwingWorker<Boolean, String>() {

      boolean error = false;

      @Override
      protected void done() {
        progressBar.setVisible(false);
        // FileUtils.cleanDirectory(GameUpdater.tempDir);
        if (!error) {
          runGame();
        }
        this.cancel(true);
      }

      @Override
      protected Boolean doInBackground() throws Exception {
        try {
          if (mcUpdate) {
            gameUpdater.updateMC();
          }
          if (modpackUpdate) {
            gameUpdater.updateModPackMods();
          }
        } catch (NoMirrorsAvailableException e) {
          JOptionPane.showMessageDialog(getParent(), "No Mirrors Are Available to download from!\nTry again later.");
        } catch (Exception e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(getParent(), "Update Failed!");
          error = true;
          enableUI();
          this.cancel(true);
          return false;
        }
        return true;
      }

      @Override
      protected void process(List<String> chunks) {
        progressBar.setString(chunks.get(0));
      }
    };
    updateThread.execute();
  }

  public void enableUI() {
    loginButton.setEnabled(true);
    optionsButton.setEnabled(true);
    modsButton.setEnabled(true);
    loginSkin1.setEnabled(true);
    loginSkin2.setEnabled(true);
  }

  private Cipher getCipher(int mode, String password) throws Exception {
    Random random = new Random(43287234L);
    byte[] salt = new byte[8];
    random.nextBytes(salt);
    PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, 5);

    SecretKey pbeKey = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(new PBEKeySpec(password.toCharArray()));
    Cipher cipher = Cipher.getInstance("PBEWithMD5AndDES");
    cipher.init(mode, pbeKey, pbeParamSpec);
    return cipher;
  }

  public void runGame() {
    if (ModPackListYML.currentModPack.equals("technicssp")) {
      File temp = new File(GameUpdater.modsDir, "industrialcraft-2-client_1.64.jar");
      if (temp.exists())
        temp.delete();
    }

    LauncherFrame launcher = new LauncherFrame();
    launcher.setLoginForm(this);
    int result = (Main.isOffline) ? launcher.runGame(null, null, null, null) : launcher.runGame(values.getSelectedProfile().getName(), values.getSessionId(), "0", pass);
    if (result == LauncherFrame.SUCCESSFUL_LAUNCH) {
      LoginForm.updateDialog.dispose();
      LoginForm.updateDialog = null;
      setVisible(false);
      Main.loginForm = null;

      dispose();
    } else if (result == LauncherFrame.ERROR_IN_LAUNCH) {
      loginButton.setEnabled(true);
      optionsButton.setEnabled(true);
      modsButton.setEnabled(true);
      loginSkin1.setEnabled(true);
      loginSkin2.setEnabled(true);
      progressBar.setVisible(false);
    }

    this.success = result;
    // Do nothing for retrying launch
  }

  @Override
  public void windowOpened(WindowEvent e) {
    tumblerFeed = new TumblerFeedParsingWorker(editorPane);
    tumblerFeed.execute();

    // If a modpack is selected, look in multiple candidate screenshot locations and pick one at random
    try {
      String pack = ModPackListYML.currentModPack;
      java.util.List<File> candidates = new java.util.ArrayList<>();
      if (pack != null && !pack.isEmpty()) {
        // 1) standard modpack directory under workDir/pack/resources/screenshots
        if (ModPackListYML.currentModPackDirectory != null) {
          candidates.add(new File(ModPackListYML.currentModPackDirectory, "resources/screenshots"));
        }
        // 2) working dir / pack / resources / screenshots
        candidates.add(new File(PlatformUtils.getWorkingDirectory(), pack + File.separator + "resources" + File.separator + "screenshots"));
        // 3) working dir / pack / screenshots (some setups use this path)
        candidates.add(new File(PlatformUtils.getWorkingDirectory(), pack + File.separator + "screenshots"));
        // 4) user's home .techniclauncher/<pack>/screenshots (common local cache)
        candidates.add(new File(System.getProperty("user.home"), ".techniclauncher" + File.separator + pack + File.separator + "screenshots"));
        // 5) legacy: GameUpdater.workDir / pack / resources / screenshots
        candidates.add(new File(GameUpdater.workDir, pack + File.separator + "resources" + File.separator + "screenshots"));
      }

      for (File screenshotsDir : candidates) {
        if (screenshotsDir != null && screenshotsDir.exists() && screenshotsDir.isDirectory()) {
          File[] imgs = screenshotsDir.listFiles((dir, name) -> {
            String ln = name.toLowerCase();
            return ln.endsWith(".png") || ln.endsWith(".jpg") || ln.endsWith(".jpeg") || ln.endsWith(".gif");
          });
          if (imgs != null && imgs.length > 0) {
            java.util.Random rnd = new java.util.Random();
            File pick = imgs[rnd.nextInt(imgs.length)];
            try {
              Image img = ImageIO.read(pick);
              if (img != null) {
                contentPane.setBackgroundImage(new ImageIcon(img));
              } else {
                contentPane.setBackgroundImage(new ImageIcon(pick.getPath()));
              }
            } catch (java.io.IOException ioe) {
              contentPane.setBackgroundImage(new ImageIcon(pick.getPath()));
            }
            return;
          }
        }
      }
    } catch (Exception ex) {
      // fallback to default behavior
      ex.printStackTrace();
    }

    ImageIcon backgroundImage = new ImageIcon(LoginForm.class.getResource("/org/spoutcraft/launcher/backgrounds/launcher_background.jpg"));
    contentPane.setBackgroundImage(backgroundImage);
    contentPane.setBackgroundOpacity(0.2f);
  }

  @Override
  public void windowClosing(WindowEvent e) {
  }

  @Override
  public void windowClosed(WindowEvent e) {
    if (success == LauncherFrame.ERROR_IN_LAUNCH) {
      Util.log("Exiting the Technic Launcher");
      System.exit(0);
    }
  }

  @Override
  public void windowIconified(WindowEvent e) {
  }

  @Override
  public void windowDeiconified(WindowEvent e) {
  }

  @Override
  public void windowActivated(WindowEvent e) {
  }

  @Override
  public void windowDeactivated(WindowEvent e) {
  }

  private static final class UserPasswordInformation {

    public boolean isHash;
    public byte[]  passwordHash = null;
    public String  password     = null;
    private String profileName  = "";

    public UserPasswordInformation(String pass, String profileName) {
      this(pass);
      this.setProfileName(profileName);
    }

    public UserPasswordInformation(String pass) {
      isHash = false;
      password = pass;
    }

    public UserPasswordInformation(byte[] hash) {
      isHash = true;
      passwordHash = hash;
    }

    public Boolean hasProfileName() {
      if (getProfileName().equals("")) {
        return false;
      }
      return true;
    }

    /**
     * @return the profileName
     */
    public String getProfileName() {
      return profileName;
    }

    /**
     * @param profileName
     *          the profileName to set
     */
    public void setProfileName(String profileName) {
      this.profileName = profileName;
    }
  }
}
