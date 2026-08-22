package org.spoutcraft.launcher.gui;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

class BackgroundPanel extends JPanel {
  private static final long serialVersionUID    = 1L;
  private ImageIcon         backgroundImageIcon = null;
  private Image             backgroundImage     = null;

  public void setBackgroundImage(ImageIcon imageIcon) {
    backgroundImageIcon = imageIcon;
    backgroundImage = backgroundImageIcon.getImage();
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    // Draw the background image scaled to fill the panel
    if (backgroundImage != null) {
      g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
    }
  }
}