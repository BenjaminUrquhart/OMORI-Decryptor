package net.benjaminurquhart.rpgdump;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.json.JSONObject;

public class RPGMakerUtil {
	
	public static List<File> defaultExclusions;
	
	private static File folder, root;
	private static List<File> files;
	private static String gameName;
	
	private static byte[] keyBytes;
	
	public static void init(File folder) throws Exception {
		RPGMakerUtil.root = null;
		RPGMakerUtil.folder = folder;
		File pkgInfo = new File(getRootAssetFolder(), "package.json");
		System.out.println(pkgInfo.getAbsolutePath());
		if(!pkgInfo.exists()) {
			throw new IllegalArgumentException("Invalid RPGMaker game folder");
		}
		
		JSONObject info = new JSONObject(Main.readString(pkgInfo));
		String name = info.optString("name", "???");
		System.out.println("Game: " + name);
		gameName = name;
		defaultExclusions = Arrays.asList(
				new File(getRootAssetFolder(), "gomori"),
				new File(getRootAssetFolder(), "mods")
		);
	}
	
	public static String getGameName() {
		return gameName;
	}
	
	public static void deobfuscateGame() throws Exception {
		files = Main.getFilesWithExts(folder, defaultExclusions, ".rpgmvp", ".rpgmvo");
		System.out.println(files.size() + " obfuscated file(s) found.");
		
		findObfuscationKey();
		if(keyBytes == null) {
			throw new IllegalArgumentException("RPG Maker key not found");
		}
		
		int index = 1;
		byte[] bytes;
		File backup;
		for(File file : files) {
			try {
				Main.updateProgressBar("Deobfuscating", file.getName(), index, files.size());
				backup = new File(file.getName() + ".BASIL");
				bytes = Main.getTrimmedFile(backup.exists() ? backup : file);
				
				// Only the first 16 bytes of the file are XORed
				// because yes.
				for(int i = 0; i < keyBytes.length; i++) {
					bytes[i] ^= keyBytes[i];
				}
				Files.write(Main.getNormalFile(file).toPath(), bytes);
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			index++;
		}
	}

	public static byte[] getAssetObfuscationKey() throws Exception {
		if(keyBytes == null) {
			findObfuscationKey();
		}
		return Arrays.copyOf(keyBytes, keyBytes.length);
	}
	
	private static void findObfuscationKey() throws Exception {
		File system = new File(getRootAssetFolder(), "data/System.json");
		String key = null;
		
		byte[] bytes;
		keyBytes = new byte[16];
		
		if(system.exists()) {
			JSONObject json = new JSONObject(Main.readString(system));
			key = json.optString("encryptionKey", null);
		}
		else {
			System.out.println("Could not find System.json!");
		}
		
		if(key == null) {
			System.out.println("No asset key found!\nAttempting to brute-force...");
			
			/* This is a known-plaintext attack against the sprites.
			 * We know this is a PNG file with the first chunk being
			 * IHDR. This gives us 12 bytes of the 16-byte key by
			 * XORing our known data with the beginning of the file.
			 * 
			 * The only thing we don't know is the length of the IHDR
			 * chunk, but we can brute-force this. It shouldn't be that
			 * large. Once we find the length, we have the key.
			 */
			
			// Find the smallest PNG. This way, we can try to minimize the time
			// spent brute-forcing the length.
			System.out.println("Finding a suitable PNG to attack...");
			File candidate = files.stream()
								  .filter(f -> f.getName().endsWith(".rpgmvp"))
								  .sorted((a,b) -> (int)(a.length() - b.length()))
								  .findFirst()
								  .orElse(null);
			
			if(candidate == null) {
				// No images found, I'm lazy and don't want to do this on oggs.
				System.out.println("Could not find an image to brute-force the key with!");
				return;
			}
			
			long start = System.currentTimeMillis();
			System.out.println("Target file: " + candidate.getAbsolutePath());
			
			bytes = Files.readAllBytes(candidate.toPath());
			ByteBuffer buff = ByteBuffer.wrap(bytes), keyBuff = ByteBuffer.wrap(keyBytes);
			keyBuff.order(ByteOrder.BIG_ENDIAN);
			buff.order(ByteOrder.BIG_ENDIAN);
			keyBuff.mark();
			buff.mark();
			
			long signature = buff.getLong();
			long other = buff.getLong();
			
			long ver = other >> 40;
			long rem = other & ((1L << 40) - 1);
			
			if(signature != 0x5250474d56000000L) {
				throw new IllegalStateException("Not an RPG Maker obfuscated file.");
			}
			
			buff.reset();
			
			StringBuilder sb = new StringBuilder();
			byte b = buff.get();
			
			while(b != 0) {
				sb.append((char)b);
				b = buff.get();
			}
			
			System.out.printf("Header: %08x%08x (%s v%d, r%d)\n", signature, other, sb, ver, rem);
			
			bytes = Arrays.copyOfRange(bytes, 16, bytes.length);
			buff = ByteBuffer.wrap(bytes);
			buff.mark();
			
			System.out.println("Trimmed size: " + bytes.length + " bytes");
			buff.order(ByteOrder.BIG_ENDIAN);
			
			keyBuff.reset();
			buff.reset();
			
			// Known PNG header segments
			final int HEADER_1 = 0x89504e47, HEADER_2 = 0x0d0a1a0a, HEADER_4 = 0x49484452;
			keyBuff.mark();
			buff.mark();
			
			// Prepare for brute-force
			keyBuff.putInt(buff.getInt() ^ HEADER_1);
			keyBuff.putInt(buff.getInt() ^ HEADER_2);
			keyBuff.putInt(12, buff.getInt(12) ^ HEADER_4);
			
			buff.reset();
			
			buff.putInt(HEADER_1);
			buff.putInt(HEADER_2);
			buff.putInt(12, HEADER_4);
			
			InputStream stream = new ByteArrayInputStream(bytes);
			stream.mark(bytes.length + 1);
			
			int length = buff.getInt(8);
			int realLen = 1;
			
			// Check for overflow
			while(realLen > 0) {
				try {
					if(realLen % 1000 == 0) {
						System.out.println(realLen);
					}
					
					// Test length
					buff.putInt(8, realLen);
					ImageIO.read(stream);
					
					// Found correct length
					break;
				}
				catch(Exception e) {
					
					// Try again
					realLen++;
				}
				finally {
					stream.reset();
				}
			}
			key = "";
			stream.close();
			
			// Write last part of the key
			keyBuff.putInt(8, realLen ^ length);
			
			for(int i = 0; i < keyBytes.length; i++) {
				key += String.format("%02x", keyBytes[i]);
			}
			
			System.out.println("Found asset key in " + (System.currentTimeMillis() - start) + "ms (Took " + realLen + " attempts)");
		}
		else {
			Main.hexStringToBytes(key, keyBytes);
		}
	}
	
	public static File getRootAssetFolder() {
		if(root == null) {
			File file = new File(folder, "www");
			if(file.exists()) {
				root = file;
			}
			else {
				root = new File(folder, "Contents/Resources/app.nw");
			}
		}
		return root;
	}
	
	public static File getSteamFolder() {
		String os = System.getProperty("os.name");
		File folder;
		if(os.startsWith("Windows")) {
			folder = new File("C:\\Program Files (x86)\\Steam");
			if(!folder.exists()) {
				folder = new File("C:\\Program Files\\Steam");
			}
		}
		else if(os.startsWith("Mac")) {
			folder = new File(System.getenv("HOME") + "/Library/Application Support/Steam/");
		}
		else {
			folder = new File(System.getenv("HOME") + "/.local/share/Steam/");
		}
		return folder;
	}
}
