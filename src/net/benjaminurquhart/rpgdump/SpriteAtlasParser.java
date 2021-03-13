package net.benjaminurquhart.rpgdump;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class SpriteAtlasParser {

	@SuppressWarnings("unchecked")
	public static void parseSpritesFromAtlas(File folder) throws Exception {
		File atlasFile = new File(Main.outFolder, "data/Atlas.yaml");
		if(atlasFile.exists()) {
			System.out.println("Attempting to parse spritesheet atlas...");
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			
			Map<String, Map<String, Object>> atlas = ((Map<String, Map<String, Map<String, Object>>>)mapper.readValue(atlasFile, HashMap.class)).get("source");
			
			File outDir = new File(Main.outFolder, "parsed_sprites_from_atlas");
			outDir.mkdirs();
			
			Map<String, BufferedImage> textures = new HashMap<>();
			
			File outFile;
			int x, y, width, height;
			Map<String, Object> atlasEntry;
			Map<String, Integer> sourceRect;
			BufferedImage texture, output;
			int index = 1;
			for(Map.Entry<String, Map<String, Object>> entry : atlas.entrySet()) {
				Main.updateProgressBar("Parsing sprites", entry.getKey(), index++, atlas.size());
				outFile = new File(outDir, entry.getKey());
				outFile.getParentFile().mkdirs();
				atlasEntry = entry.getValue();
				texture = textures.computeIfAbsent("img/atlases/" + atlasEntry.get("atlasName") + ".png", source -> {
					try {
						File file = new File(Main.outFolder, source);
						if(file.exists()) {
							return ImageIO.read(file);
						}
						System.out.println("Atlas not found: " + file.getName());
					}
					catch(Exception e) {
						e.printStackTrace(System.out);
					}
					return null;
				});
				
				if(texture == null) {
					continue;
				}
				
				sourceRect = (Map<String, Integer>)atlasEntry.get("sourceRect");
				
				try {
					x = sourceRect.get("x");
					y = sourceRect.get("y");
					width = sourceRect.get("width");
					height = sourceRect.get("height");
					if(x < 0 || y < 0 || x + width > texture.getWidth() || y + height > texture.getHeight()) {
						System.out.println("Error: invalid rect for " + outFile.getName() + ": coordinates and dimensions go out of bounds");
						System.out.printf("Rect:        {x: %d, y: %d, w: %d, h: %d}\n", x, y, width, height);
						System.out.printf("Spritesheet: {x: %d, y: %d, w: %d, h: %d}\n", 0, 0, texture.getWidth(), texture.getHeight());
						continue;
					}
					output = texture.getSubimage(x, y, width, height);
					ImageIO.write(output, "png", outFile);
				}
				catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
			
			//System.out.println(atlas);
		}
		else {
			System.out.println("No atlas found, skipping spritesheet parsing.");
		}
	}
}
