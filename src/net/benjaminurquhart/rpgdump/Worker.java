package net.benjaminurquhart.rpgdump;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class Worker extends SwingWorker<Void, Void> {
	
	private File folder;
	private volatile boolean exit = false;
	
	public Worker(File folder) throws FileNotFoundException {
		if(!folder.exists()) {
			throw new FileNotFoundException("Folder not found:" + folder.getAbsolutePath());
		}
		if(!folder.isDirectory()) {
			throw new IllegalArgumentException("Provided path is not a folder: " + folder.getAbsolutePath());
		}
		this.folder = folder;
	}
	
	public Void doInBackground() {
		try {
			exit = true;
			UI ui = UI.getInstance();
			
			ui.progressBar.setIndeterminate(true);
			ui.progressBar.setString("Scanning...");
			
			Main.outFolder = new File(ui.dest.getText());
			Main.outFolder.mkdirs();
			
			RPGMakerUtil.init(folder);
			RPGMakerUtil.deobfuscateGame();
			
			if(RPGMakerUtil.getGameName().equals("OMORI")) {
				try {
					OmoriUtil.init(folder, null);
					OmoriUtil.decrypt();
				}
				catch(Throwable e) {
					Toolkit.getDefaultToolkit().beep();
					JOptionPane.showMessageDialog(null, "Failed to decrypt OMORI. Music, images, and video are still available.", "Decryption error", JOptionPane.ERROR_MESSAGE);
					e.printStackTrace(System.out);
				}
			}
		}
		catch(Throwable e) {
			UI.getInstance().onError(e);
			exit = false;
		}
		return null;
	}
	
	public void done() {
		if(exit) {
			try {
				Desktop.getDesktop().open(new File(UI.getInstance().dest.getText()));
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			System.exit(0);
		}
	}
}
