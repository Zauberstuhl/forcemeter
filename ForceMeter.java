/*
 * DamageMeter for Star Wars the old Republic
 * Copyright (C) 2016  Lukas Matt <lukas@zauberstuhl.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;

import java.util.regex.Pattern;
import java.util.Hashtable;
import java.util.Date;

import java.text.SimpleDateFormat;

public class ForceMeter {

  private static Hashtable<String, Long> damageDone
    = new Hashtable<String, Long>();

  private static JFrame frame = new JFrame("Forcemeter");

  private static JTable table = new JTable(new DefaultTableModel(new Object[]{"Name", "Damage (DPS)"}, 0));

  private File combatLog(String cwd) {
    Long lastMod = 0L;
    File logFile = null;
    File[] files = findCombatLogs(cwd);
    for (File file : files) {
      if (file.lastModified() > lastMod) {
        logFile = file;
        lastMod = file.lastModified();
      }
    }
    return logFile;
  }

  class ParserThread extends Thread {

    private String cwd = null;

    public ParserThread(String cwd) {
      this.cwd = cwd;
    }

    public void run() {
      if (combatLog(this.cwd) == null) {
        System.out.println("No combat log found! Abort.");
        System.exit(1);
      }

      long lastKnownPosition = 0L;
      try {
        while(true) {
          File combatLogFile = combatLog(this.cwd);
          long fileLength = combatLogFile.length();
          if (fileLength > lastKnownPosition) {
            RandomAccessFile randomAccessFile = new RandomAccessFile(combatLogFile, "r");
            randomAccessFile.seek(lastKnownPosition);
            String combatLogLine = null;
            while ((combatLogLine = randomAccessFile.readLine()) != null) {
              try {
                parseCombatLogLine(combatLogLine);
              } catch (Exception e) {
                System.out.println(e.getMessage());
              }
            }
            lastKnownPosition = randomAccessFile.getFilePointer();
            randomAccessFile.close();
          }

          //frame.toFront();
          Thread.sleep(500);
        }
      } catch (Exception e) {
        System.out.println(e.getMessage());
        System.exit(1);
      }
    }
  }

  class BorderPanel extends JPanel {
    private JLabel label;
    int pX, pY;

    public BorderPanel() {
      label = new JLabel(" X ");
      label.setOpaque(true);
      label.setBackground(Color.RED);
      label.setForeground(Color.WHITE);

      setBackground(Color.black);
      setLayout(new FlowLayout(FlowLayout.RIGHT));

      add(label);

      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          System.exit(0);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent me) {
          pX = me.getXOnScreen();
          pY = me.getYOnScreen();
        }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent me) {
          int x = me.getXOnScreen();
          int y = me.getYOnScreen();
          frame.setLocation(
            frame.getLocation().x + x - pX,
            frame.getLocation().y + y - pY
          );
          pX = x;
          pY = y;
        }
      });
    }
  }

  private static int existsInTable(JTable table, String name) {
    int rowCount = table.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      String entry = table.getValueAt(i, 0).toString();
      if (entry.equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  private static File[] findCombatLogs(String dirName) {
    File dir = new File(dirName);
    return dir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String filename) {
        return filename.endsWith(".txt");
      }
    });
  }

  private static void parseCombatLogLine(String line) throws Exception {
    //EnterCombat
    if (Pattern.matches("^.*EnterCombat.*$", line)) {
      String curTime = line.replaceAll("^\\[([\\d\\.:]+?)\\].+?$", "$1");
      SimpleDateFormat parser = new SimpleDateFormat("HH:mm:ss.SS");
      Date enterCombat = parser.parse(curTime);

      damageDone.clear();
      damageDone.put("_EnterCombat", enterCombat.getTime());

      DefaultTableModel model = (DefaultTableModel) table.getModel();
      model.setRowCount(0);
      return;
    }
    //ExitCombat
    if (Pattern.matches("^.*ExitCombat.*$", line)) {
      return;
    }
    //Damage
    //[16:24:56.988] [@Cerb Erus] [Exchange Goon {838893012254720}:24477366334413] [Shock {808308550139904}] [ApplyEffect {836045448945477}: Damage {836045448945501}] (516 energy {836045448940874}) <516>
    if (Pattern.matches("^.*Damage.*$", line)) {
      Long curDamage = Long.parseLong(line.replaceAll("^.*\\<(\\d+?)\\>$", "$1"));
      String curName = line.replaceAll("^.+\\[\\@(.+?)\\].+?$", "$1");
      String curTime = line.replaceAll("^\\[([\\d\\.:]+?)\\].+?$", "$1");
      SimpleDateFormat parser = new SimpleDateFormat("HH:mm:ss.SS");
      Date time = parser.parse(curTime);
      Long oldDamage = damageDone.get(curName);
      if (oldDamage == null) {
        damageDone.put(curName, curDamage);
      } else {
        damageDone.put(curName, curDamage+oldDamage);
      }

      Long damage = damageDone.get(curName);
      Long timeDiff = (time.getTime() - damageDone.get("_EnterCombat")) / 1000;

      Long dps = 0L;
      if (timeDiff > 0) {
        dps = damage/timeDiff;
      }

      DefaultTableModel model = (DefaultTableModel) table.getModel();
      int index = existsInTable(table, curName);
      System.out.println(curName + ": " + damage+" ("+dps+" dps)");
      if (index > -1) {
        model.setValueAt(damage+" ("+dps+" dps)", index, 1);
      } else {
        model.addRow(new Object[]{curName, damage+" ("+dps+" dps)"});
      }

      //label.setText(curName+": "+damage+" ("+dps+" dps)");
      //label.revalidate();
      //label.repaint();
      return;
    }
  }

  private void run(String[] args) {
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    JScrollPane scrollPane = new JScrollPane(table);

    // initial size of the main frame
    frame.setPreferredSize(new Dimension(250, 200));

    frame.setBackground(Color.gray);
    frame.add(scrollPane, BorderLayout.CENTER);

    // dimension of the main window
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    // alwayys in foreground
    frame.setAlwaysOnTop(true);
    frame.setFocusableWindowState(false);
    frame.setLocationByPlatform(true);
    frame.setUndecorated(true);
    frame.add(new BorderPanel(), BorderLayout.PAGE_START);

    frame.pack();
    frame.setVisible(true);

    // startup log parser
    new ParserThread(args[0]).start();
  }

  public static void main(String[] args) {
    if (!(args.length > 0 && args[0] != null)) {
      System.out.println("Please specify your log directory! Abort.");
      System.exit(1);
    }

    // startup gui
    new ForceMeter().run(args);
  }
}
