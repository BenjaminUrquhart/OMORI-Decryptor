package net.benjaminurquhart.rpgdump;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JProgressBar;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class OmoriUtil {
	
	public static final String KEY_HASH = "b1d50d2686248fc493b71cd490cb88ac75e71caff236fdb4ab9fa78a36319e11";
	public static final Pattern KEY_PATTERN = Pattern.compile("\\-\\-([0-9a-f]{32})");
	
	private static File folder;
	private static String decryptionKey;
	
	public static void init(File folder, String decryptionKey) throws Exception {
		JProgressBar progressBar = UI.getInstance().progressBar;
		progressBar.setIndeterminate(true);
		progressBar.setString("Initializing decryptor...");
		
		if(!hash(decryptionKey).equals(KEY_HASH)) {
			File steamAppInfo = new File(RPGMakerUtil.getSteamFolder(), "appcache/appinfo.vdf");
			if(!steamAppInfo.exists()) {
				throw new IllegalStateException("Steam not installed");
			}
			System.out.println("Attempting to pull key from Steam appinfo...");
			System.out.println("File: " + steamAppInfo.getAbsolutePath());
			
			FileInputStream stream = new FileInputStream(steamAppInfo);
			StringBuilder sb = new StringBuilder();
			byte[] buff = new byte[1024];
			int read;
			
			while((read = stream.read(buff)) != -1) {
				for(int i = 0; i < read; i++) {
					sb.append((char)buff[i]);
				}
			}
			stream.close();
			
			Matcher matcher = KEY_PATTERN.matcher(sb.toString());
			String candidate = null;
			boolean success = false;
			
			while(matcher.find()) {
				candidate = matcher.group(1);
				if(hash(candidate).equals(KEY_HASH)) {
					success = true;
					break;
				}
			}
			if(!success) {
				throw new IllegalStateException("OMORI not Steam installed");
			}
			System.out.println("Found decryption key.");
			decryptionKey = candidate;
		}
		
		OmoriUtil.decryptionKey = decryptionKey;
		OmoriUtil.folder = folder;
	}
	
	private static List<ZipEntry> getZipEntriesWithExts(ZipFile file, String... exts) {
		return file.stream()
				   .filter(entry -> !entry.isDirectory())
				   .filter(entry -> {
					   for(String ext : exts) {
						   if(entry.getName().endsWith(ext)) {
							   return true;
						   }
					   }
					   return false;
				   }).collect(Collectors.toList());
	}
	
	public static Set<String> getDetectedMods() {
		Set<String> out = new HashSet<>(), seen = new HashSet<>();
		File modFolder = new File(RPGMakerUtil.getRootAssetFolder(), "mods");
		File gomoriFolder = new File(RPGMakerUtil.getRootAssetFolder(), "gomori");
		
		if(modFolder.exists() && gomoriFolder.exists()) {
			List<File> looseMods = Main.getFilesWithExts(modFolder, null, "mod.json");
			List<File> compressedMods = Main.getFilesWithExts(modFolder, null, ".zip");
			
			List<ZipEntry> compressedEntries;
			
			StringBuilder sb = new StringBuilder();
			InputStream stream;
			JSONObject config;
			String json;
			int b;
			
			for(File mod : looseMods) {
				System.out.println(mod.getAbsolutePath());
				try {
					json = Files.readString(mod.toPath());
					//System.out.println(json);
					config = new JSONObject(json);
					if(seen.add(config.optString("id"))) {
						out.add(String.format("%s v%s (%s)", config.optString("name", mod.getName()), config.optString("version", "???"), config.optString("id", mod.getName())));
					}
				}
				catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
			
			for(File mod : compressedMods) {
				try(ZipFile zip = new ZipFile(mod)) {
					compressedEntries = getZipEntriesWithExts(zip, "mod.json");
					for(ZipEntry entry : compressedEntries) {
						System.out.println(mod.getAbsolutePath() + ":" + entry.getName());
						sb.delete(0, sb.length());
						stream = zip.getInputStream(entry);
						while((b = stream.read()) != -1) {
							sb.append((char)b);
						}
						stream.close();
						//System.out.println(sb);
						config = new JSONObject(sb.toString());
						if(seen.add(config.optString("id"))) {
							out.add(String.format("%s v%s (%s)", config.optString("name", mod.getName()), config.optString("version", "???"), config.optString("id", mod.getName())));
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
		}
		return out;
	}

	public static void decrypt() throws Exception {
		JProgressBar progressBar = UI.getInstance().progressBar;
		progressBar.setString("Decrypting");
		
		if(decryptionKey != null) {
			System.out.println("Finding files to decrypt...");
			List<File> files = Main.getFilesWithExts(folder, RPGMakerUtil.defaultExclusions, ".OMORI", ".AUBREY", ".PLUTO", ".HERO", ".KEL");
			if(files.isEmpty()) {
				System.out.println("Nothing found!");
			}
			else {
				FileOutputStream stream;
				ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
				
				byte[] secret = decryptionKey.getBytes("utf-8"), output, iv, bytes;
				SecretKeySpec spec = new SecretKeySpec(secret, "AES");
				Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
				
				ObjectMapper mapper;				
				String tmpJson;
				File normal;
				
				int index = 1;
				for(File file : files) {
					try {
						Main.updateProgressBar("Decrypting", file.getName(), index++, files.size());
						bytes = Files.readAllBytes(file.toPath());
						
						iv = Arrays.copyOfRange(bytes, 0, 16);
						bytes = Arrays.copyOfRange(bytes, 16, bytes.length);
						
						cipher.init(Cipher.DECRYPT_MODE, spec, new IvParameterSpec(iv));
						
						normal = Main.getNormalFile(file);
						decrypted.reset();
						
						output = cipher.update(bytes);
						if(output != null) {
							decrypted.write(output);
						}
						decrypted.write(cipher.doFinal());
						
						stream = new FileOutputStream(normal);
						
						if(normal.getName().endsWith(".json")) {
							try {
								mapper = new ObjectMapper();
								mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
								
								tmpJson = new String(decrypted.toByteArray(), "utf-8");
								tmpJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(tmpJson, Object.class));
								
								stream.write(tmpJson.getBytes("utf-8"));
							}
							catch(Exception e) {
								System.out.println("\u001b[1000D\u001b[2KFailed to beautify json: " + e);
								stream.write(decrypted.toByteArray());
							}
						}
						else {
							stream.write(decrypted.toByteArray());
						}
						stream.flush();
						stream.close();
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		else {
			throw new IllegalStateException("Decryption key not found");
		}
	}

	
	private static String hash(String string) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("sha-256");
		byte[] hash = digest.digest(String.valueOf(string).getBytes("utf-8"));
		return Main.bytesToHexString(hash);
	}
}
