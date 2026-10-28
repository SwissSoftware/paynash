package ch.paynash.pos.menu;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ch.paynash.pos.common.ApiException;

/**
 * Validates uploaded images, stores a WebP display version (max 512 px) under a content-hash file name, and drops
 * the original (design D8).
 */
@Component
public class ImageStore {

	static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

	static final int MAX_EDGE_PX = 512;

	/** Guards against small files that decode to huge bitmaps. */
	private static final long MAX_SOURCE_PIXELS = 50_000_000L;

	private static final Pattern REF = Pattern.compile("[0-9a-f]{32}\\.webp");

	private final Path dir;

	public ImageStore(@Value("${pos.image-dir}") Path dir) {
		this.dir = dir.toAbsolutePath();
		try {
			Files.createDirectories(this.dir);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot create image directory " + this.dir, ex);
		}
	}

	/** Validates, converts, and stores the image. Returns its reference (file name). */
	public String store(byte[] upload) {
		if (upload.length > MAX_UPLOAD_BYTES) {
			throw reject("Image must be at most 5 MB");
		}
		if (!isJpeg(upload) && !isPng(upload) && !isWebp(upload)) {
			throw reject("Only JPEG, PNG and WebP images are accepted");
		}
		byte[] webp = encodeWebp(resize(decode(upload)));
		String ref = sha256(webp).substring(0, 32) + ".webp";
		Path target = dir.resolve(ref);
		try {
			if (!Files.exists(target)) {
				Path tmp = Files.createTempFile(dir, "upload-", ".tmp");
				Files.write(tmp, webp);
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return ref;
	}

	public Optional<Path> find(String ref) {
		if (ref == null || !REF.matcher(ref).matches()) {
			return Optional.empty();
		}
		Path path = dir.resolve(ref);
		return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
	}

	public void delete(String ref) {
		find(ref).ifPresent(path -> {
			try {
				Files.deleteIfExists(path);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		});
	}

	static boolean isJpeg(byte[] b) {
		return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
	}

	static boolean isPng(byte[] b) {
		byte[] sig = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		return b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), sig);
	}

	static boolean isWebp(byte[] b) {
		return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W'
				&& b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}

	private static BufferedImage decode(byte[] data) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			var readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw reject("The image could not be read");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_SOURCE_PIXELS) {
					throw reject("The image dimensions are too large");
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			throw reject("The image could not be read");
		}
	}

	/** Scales down so the longer edge is at most 512 px; halves in steps for better quality. */
	private static BufferedImage resize(BufferedImage source) {
		int w = source.getWidth();
		int h = source.getHeight();
		double scale = Math.min(1.0, (double) MAX_EDGE_PX / Math.max(w, h));
		int targetW = Math.max(1, (int) Math.round(w * scale));
		int targetH = Math.max(1, (int) Math.round(h * scale));
		BufferedImage current = toArgb(source);
		while (current.getWidth() != targetW || current.getHeight() != targetH) {
			int nextW = Math.max(targetW, current.getWidth() / 2);
			int nextH = Math.max(targetH, current.getHeight() / 2);
			BufferedImage next = new BufferedImage(nextW, nextH, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = next.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(current, 0, 0, nextW, nextH, null);
			g.dispose();
			current = next;
		}
		return current;
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return copy;
	}

	private static byte[] encodeWebp(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
		try (var bytes = new ByteArrayOutputStream(); var out = new MemoryCacheImageOutputStream(bytes)) {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionType(param.getCompressionTypes()[0]); // lossy
			param.setCompressionQuality(0.8f);
			writer.setOutput(out);
			writer.write(null, new IIOImage(image, null, null), param);
			out.flush();
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			writer.dispose();
		}
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static ApiException reject(String message) {
		return ApiException.validation("file", message);
	}

}
