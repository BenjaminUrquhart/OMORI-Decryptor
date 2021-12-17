package net.benjaminurquhart.rpgdump;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

public class Worker extends SwingWorker<Void, Void> {
	
	private File folder;
	private volatile boolean exit = false;
	
	public Worker(File folder) throws FileNotFoundException {
		if(!folder.exists()) {
			throw new FileNotFoundException("Folder not found: " + folder.getAbsolutePath());
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
			
			try {
				RPGMakerUtil.init(folder);
			}
			catch(IllegalArgumentException e) {
				JOptionPane.showMessageDialog(
						null, 
						"Not an RPGMaker game folder",
						"Load error", 
						JOptionPane.ERROR_MESSAGE
				);
				e.printStackTrace(System.out);
				UI.getInstance().onError(null);
				exit = false;
				return null;
			}
			RPGMakerUtil.deobfuscateGame();
			
			if(RPGMakerUtil.getGameName().equals("OMORI")) {
				try {
					OmoriUtil.init(folder, null);
					
					Set<String> mods = OmoriUtil.getDetectedMods();
					if(!mods.isEmpty()) {
						System.out.println("Mods:");
						ui.mods.add(new JLabel("Mods (will not be decrypted):"));
						mods.forEach(mod -> System.out.println("- " + mod));
						mods.forEach(mod -> ui.mods.add(new JLabel("- " + mod)));
						ui.mods.forEach(ui::add);
						ui.layout.setRows(mods.size() + 7);
					}
					else {
						ui.mods.forEach(ui::remove);
						ui.layout.setRows(6);
						ui.mods.clear();
					}
					ui.revalidate();
					
					if(!mods.isEmpty()) {
						Main.frame.pack();
					}
					
					OmoriUtil.decrypt();
				}
				catch(Throwable e) {
					Toolkit.getDefaultToolkit().beep();
					JOptionPane.showMessageDialog(
							null, 
							"Failed to decrypt OMORI. Music, images, and video are still available.\n" +
							e.getClass().getName() + ": " + e.getMessage(),
							"Decryption error", 
							JOptionPane.ERROR_MESSAGE
					);
					e.printStackTrace(System.out);
				}
				
				//SpriteAtlasParser.parseSpritesFromAtlas(folder);
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
