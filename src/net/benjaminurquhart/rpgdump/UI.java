package net.benjaminurquhart.rpgdump;

import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.filechooser.FileFilter;

public class UI extends JPanel implements ActionListener {
	
	private static final long serialVersionUID = -8751465600892495447L;
	
	public List<JLabel> mods;
	public GridLayout layout;
	public JLabel source, dest;
	public JProgressBar progressBar;
	public JButton folderSelectButton, outputSelectButton, startButton;
	
	private final JFileChooser fc = new JFileChooser();
	
	private static UI instance;
	
	public static UI getInstance() {
		if(instance == null) {
			instance = new UI();
		}
		return instance;
	}
	
	private UI() {
		mods = new ArrayList<>();
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("");
		progressBar.setValue(0);
		
		startButton = new JButton("Start");
		startButton.setActionCommand("start");
		startButton.addActionListener(this);
		
		folderSelectButton = new JButton("Select OMORI folder");
		folderSelectButton.setActionCommand("source");
		folderSelectButton.addActionListener(this);
		
		outputSelectButton = new JButton("Select Output Folder");
		outputSelectButton.setActionCommand("dest");
		outputSelectButton.addActionListener(this);
		
		String sourceFolder = RPGMakerUtil.getSteamFolder().getAbsolutePath() + "%%steamapps%%common%%OMORI%%".replace("%%", File.separator);
		
		if(System.getProperty("os.name").startsWith("Mac")) {
			sourceFolder += "OMORI.app";
		}
		
		source = new JLabel(sourceFolder, JLabel.CENTER);
		dest = new JLabel(new File("output").getAbsolutePath(), JLabel.CENTER);
		
		layout = new GridLayout(6, 1);
		setLayout(layout);
		
		add(source);
		add(folderSelectButton);
		
		add(dest);
		add(outputSelectButton);
		
		add(startButton);
		add(progressBar);
		
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setFileFilter(new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.isDirectory() && f.canWrite();
			}

			@Override
			public String getDescription() {
				return "OMORI directory";
			}
			
		});
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		switch(event.getActionCommand()) {
		case "start": {
			try {
				Worker worker = new Worker(Main.folder = new File(source.getText()));
				worker.execute();
				
				startButton.setEnabled(false);
				folderSelectButton.setEnabled(false);
				outputSelectButton.setEnabled(false);
				
				Main.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			}
			catch(FileNotFoundException e) {
				progressBar.setString(e.getMessage());
			}
		} break;
		case "source":
		case "dest": {
			if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				if(file.canWrite()) {
					if(event.getActionCommand().equals("source")) {
						source.setText(file.getAbsolutePath());
					}
					else {
						dest.setText(file.getAbsolutePath());
					}
				}
				else {
					progressBar.setString("Cannot write to selected folder");
				}
				startButton.setEnabled(true);
			}
		} break;
		}
	}
	
	public void onError(Throwable e) {
		mods.forEach(this::remove);
		layout.setRows(6);
		Main.frame.pack();
		mods.clear();
		
		if(e != null) {
			
			e.printStackTrace(System.out);
			Toolkit.getDefaultToolkit().beep();
			progressBar.setString("Internal error");
			JOptionPane.showMessageDialog(
					null, 
					"An internal error has occured.\nPlease join the OMORI community Discord server and report this to _creepersbane#2074:\n" 
					+ e.toString() 
					+ "\n" + Arrays.stream(e.getStackTrace()).map(String::valueOf).collect(Collectors.joining("\n\t")), 
					"Internal error", 
					JOptionPane.ERROR_MESSAGE
			);
		}
		else {
			progressBar.setString("");
		}
		
		startButton.setEnabled(true);
		folderSelectButton.setEnabled(true);
		outputSelectButton.setEnabled(true);
		
		Main.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
	}
}
