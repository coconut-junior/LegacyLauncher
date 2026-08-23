package org.spoutcraft.launcher.gui;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

class BackgroundPanel extends JPanel {
  private static final long serialVersionUID    = 1L;
  private ImageIcon         backgroundImageIcon = null;
  private Image             backgroundImage     = null;

  public BackgroundPanel() {
    setOpaque(true);
    setBackground(new java.awt.Color(32, 32, 32));
  }

  public void setBackgroundImage(ImageIcon imageIcon) {
    backgroundImageIcon = imageIcon;
    backgroundImage = (imageIcon != null) ? imageIcon.getImage() : null;
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (backgroundImage != null) {
      g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
    } else {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }
}