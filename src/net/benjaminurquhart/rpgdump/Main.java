package net.benjaminurquhart.rpgdump;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

public class Main {
	
	public static JFrame frame;
	
	public static File folder, outFolder;
	public static Set<String> created = new HashSet<>();
	
	public static void main(String[] args) throws Exception {
		frame = new JFrame("OMORI Decryptor");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationByPlatform(true);
		frame.add(UI.getInstance());
		
		frame.pack();
		frame.setResizable(false);
		
		frame.requestFocus();
		frame.setVisible(true);
		
		while(true);
	}
	
	public static void updateProgressBar(String state, String msg, int pos, int num) {
		JProgressBar progressBar = UI.getInstance().progressBar;
		progressBar.setIndeterminate(false);
		progressBar.setStringPainted(true);
		progressBar.setString(state + " - " + msg);
		progressBar.setMaximum(num);
		progressBar.setMinimum(0);
		progressBar.setValue(pos);
	}	
	public static byte[] hexStringToBytes(String hex, byte[] outputArr) {
		if(hex.length() % 2 == 1) {
			hex = "0" + hex;
		}
		if(outputArr == null || outputArr.length < hex.length() / 2) {
			outputArr = new byte[hex.length() / 2];
		}
		for(int i = 0; i < hex.length(); i+=2) {
			outputArr[i/2] = (byte)Integer.parseInt(hex.substring(i, i+2), 16);
		}
		return outputArr;
	}
	
	public static String bytesToHexString(byte[] bytes) {
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < bytes.length; i++) {
			s.append(String.format("%02x", bytes[i]));
		}
		return s.toString();
	}
	
	public static File getNormalFile(File file) {
		String name = file.getName();
		String path = file.getParent() + "/";
		String root = folder.getAbsolutePath();
		String assetRoot = RPGMakerUtil.getRootAssetFolder().getAbsolutePath();
		if(path.startsWith(root)) {
			path = outFolder.getAbsolutePath() + "/" + path.replaceFirst(Pattern.quote(assetRoot), "");
			if(created.add(path)) {
				new File(path).mkdirs();
			}
		}
		boolean hasExt = name.contains(".");
		int last = name.lastIndexOf(".");
		path += hasExt ? name.substring(0, last) : name;
		String ext = hasExt ? name.substring(last + 1) : ".unknown", newExt = null;
		switch(ext) {
		case "rpgmvp": newExt = ".png"; break;
		case "rpgmvo": newExt = ".ogg"; break;
		case "rpgmvm": newExt = ".m4a"; break;
		
		case "AUBREY":
		case "KEL": newExt = ".json"; break;
		
		case "PLUTO": 
		case "HERO": newExt = ".yaml"; break;
		
		case "OMORI": newExt = ".js"; break;
		default: newExt = ext;
		}
		//System.out.println(path + newExt);
		return new File(path + newExt);
	}
	
	public static byte[] getTrimmedFile(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		return Arrays.copyOfRange(bytes, 16, bytes.length);
	}
	
	public static List<File> getFilesWithExts(File folder, String... exts) {
		List<File> out = new ArrayList<>();
		
		String name;
		
		for(File file : folder.listFiles()) {
			if(file.isDirectory()) {
				out.addAll(getFilesWithExts(file, exts));
			}
			else {
				name = file.getName();
				for(String ext : exts) {
					if(name.endsWith(ext)) {
						out.add(file);
						break;
					}
				}
			}
		}
		
		return out;
	}
}
