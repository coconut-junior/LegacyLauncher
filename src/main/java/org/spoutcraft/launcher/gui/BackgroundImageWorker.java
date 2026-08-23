package org.spoutcraft.launcher.gui;

import java.awt.Image;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingWorker;

import org.spoutcraft.launcher.async.Download;

public class BackgroundImageWorker extends SwingWorker<Object, Object> {

  private static final String SPLASH_URL = "https://mirror.technicpack.net/Technic/splash/01.png";
  private File                backgroundImage;
  private BackgroundPanel     background;

  public BackgroundImageWorker(File backgroundImage, BackgroundPanel background) {
    this.backgroundImage = backgroundImage;
    this.background = background;
  }

  @Override
  protected Object doInBackground() {
    try {
      if (!backgroundImage.exists()) {
        Download download = new Download(SPLASH_URL, backgroundImage.getPath());
        download.run();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  protected void done() {
    try {
      if (backgroundImage != null && backgroundImage.exists()) {
        Image image = ImageIO.read(backgroundImage);
        if (image != null) {
          background.setBackgroundImage(new ImageIcon(image));
          return;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    ImageIcon fallback = new ImageIcon(getClass().getResource("/org/spoutcraft/launcher/splash.gif"));
    if (fallback.getImageLoadStatus() == java.awt.MediaTracker.COMPLETE) {
      background.setBackgroundImage(fallback);
    } else {
      background.setBackgroundImage(new ImageIcon(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
    }
  }
}
