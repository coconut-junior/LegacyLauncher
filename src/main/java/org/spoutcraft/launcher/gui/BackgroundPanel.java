package org.spoutcraft.launcher.gui;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

class BackgroundPanel extends JPanel {
  private static final long serialVersionUID    = 1L;
  private ImageIcon         backgroundImageIcon = null;
  private Image             backgroundImage     = null;
  private float             backgroundOpacity   = 0.2f;

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

  public void setBackgroundOpacity(float opacity) {
    backgroundOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (backgroundImage != null) {
      Graphics2D graphics = (Graphics2D) g.create();
      graphics.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, backgroundOpacity));
      graphics.setColor(java.awt.Color.WHITE);
      graphics.fillRect(0, 0, getWidth(), getHeight());
      graphics.dispose();
    } else {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }
}