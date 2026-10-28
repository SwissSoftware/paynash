package ch.paynash.pos.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

class ImageUploadTest extends MenuTestSupport {

	private static final Path IMAGE_DIR = Path.of("target/test-images");

	private long margherita;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		margherita = createItem(createCategory("Pizzas"), "Margherita", "food", "18.00");
	}

	@Test
	void validJpegIsStoredAsWebpWithMax512PxAndServedWithCacheHeaders() throws Exception {
		byte[] jpeg = noiseImage(1600, 1200, "jpg");
		assertThat(jpeg.length).isBetween(1_000_000, 5 * 1024 * 1024);

		String url = imageUrl(upload(margherita, "pizza.jpg", jpeg).andExpect(status().isOk()));
		assertThat(url).matches("/images/[0-9a-f]{32}\\.webp");

		var response = mvc.perform(get(url))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
			.andReturn()
			.getResponse();
		byte[] stored = response.getContentAsByteArray();
		assertThat(ImageStore.isWebp(stored)).isTrue();
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stored));
		assertThat(decoded.getWidth()).isEqualTo(512);
		assertThat(decoded.getHeight()).isEqualTo(384);
	}

	@Test
	void pngAndWebpAreAccepted() throws Exception {
		upload(margherita, "a.png", noiseImage(300, 200, "png")).andExpect(status().isOk());
		byte[] webp = mvc.perform(get(imageUrl(upload(margherita, "a.png", noiseImage(100, 100, "png")))))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		upload(margherita, "b.webp", webp).andExpect(status().isOk());
	}

	@Test
	void smallImagesAreNotUpscaled() throws Exception {
		String url = imageUrl(upload(margherita, "s.png", noiseImage(200, 100, "png")));
		byte[] stored = mvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray();
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stored));
		assertThat(decoded.getWidth()).isEqualTo(200);
	}

	@Test
	void pdfIsRejectedAndOldImageStays() throws Exception {
		String before = imageUrl(upload(margherita, "pizza.jpg", noiseImage(400, 300, "jpg")));
		byte[] pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes();

		upload(margherita, "menu.pdf", pdf).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.file").value("Only JPEG, PNG and WebP images are accepted"));

		assertImageUnchanged(before);
	}

	@Test
	void sixMegabyteFileIsRejectedAndOldImageStays() throws Exception {
		String before = imageUrl(upload(margherita, "pizza.jpg", noiseImage(400, 300, "jpg")));
		byte[] big = Arrays.copyOf(noiseImage(400, 300, "jpg"), 6 * 1024 * 1024);

		upload(margherita, "huge.jpg", big).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.file").value("Image must be at most 5 MB"));

		assertImageUnchanged(before);
	}

	@Test
	void corruptImageIsRejectedAndOldImageStays() throws Exception {
		String before = imageUrl(upload(margherita, "pizza.jpg", noiseImage(400, 300, "jpg")));
		byte[] corrupt = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5 };

		upload(margherita, "broken.jpg", corrupt).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.file").value("The image could not be read"));

		assertImageUnchanged(before);
	}

	@Test
	void replacingDeletesTheOldFile() throws Exception {
		String first = imageUrl(upload(margherita, "a.jpg", noiseImage(300, 300, "jpg")));
		String second = imageUrl(upload(margherita, "b.jpg", noiseImage(300, 300, "jpg")));
		assertThat(second).isNotEqualTo(first);
		assertThat(IMAGE_DIR.resolve(fileName(first))).doesNotExist();
		assertThat(IMAGE_DIR.resolve(fileName(second))).exists();
	}

	@Test
	void removeImage() throws Exception {
		String url = imageUrl(upload(margherita, "a.jpg", noiseImage(300, 300, "jpg")));
		mvc.perform(delete("/admin/items/" + margherita + "/image").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imageUrl").doesNotExist());
		assertThat(IMAGE_DIR.resolve(fileName(url))).doesNotExist();
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void imageSharedByTwoItemsIsKeptWhileStillUsed() throws Exception {
		long funghi = createItem(createCategory("More"), "Funghi", "food", "20.00");
		byte[] same = noiseImage(300, 300, "png");
		String url = imageUrl(upload(margherita, "a.png", same));
		assertThat(imageUrl(upload(funghi, "a.png", same))).isEqualTo(url);

		mvc.perform(delete("/admin/items/" + margherita + "/image").header(HttpHeaders.AUTHORIZATION, bearer(admin)));
		mvc.perform(get(url)).andExpect(status().isOk());
	}

	@Test
	void imagesArePublicForGetAndHead() throws Exception {
		String url = imageUrl(upload(margherita, "a.jpg", noiseImage(100, 100, "jpg")));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(url))
			.andExpect(status().isOk());
	}

	@Test
	void imageNamesCannotEscapeTheImageDirectory() throws Exception {
		mvc.perform(get("/images/..%2Fapplication.yml")).andExpect(status().is4xxClientError());
		mvc.perform(get("/images/notanimage.txt")).andExpect(status().isNotFound());
	}

	@Test
	void waiterCannotUpload() throws Exception {
		String waiter = waiterToken();
		mvc.perform(multipart("/admin/items/" + margherita + "/image")
			.file(new MockMultipartFile("file", "a.jpg", "image/jpeg", noiseImage(100, 100, "jpg")))
			.header(HttpHeaders.AUTHORIZATION, bearer(waiter))).andExpect(status().isForbidden());
	}

	private void assertImageUnchanged(String before) throws Exception {
		mvc.perform(get("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$[0].imageUrl").value(before));
		mvc.perform(get(before)).andExpect(status().isOk());
	}

	private ResultActions upload(long itemId, String name, byte[] data) throws Exception {
		return mvc.perform(multipart("/admin/items/" + itemId + "/image")
			.file(new MockMultipartFile("file", name, "application/octet-stream", data))
			.header(HttpHeaders.AUTHORIZATION, bearer(admin)));
	}

	private static String imageUrl(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.imageUrl");
	}

	private static String fileName(String url) {
		return url.substring("/images/".length());
	}

	private static final Random RANDOM = new Random();

	static byte[] noiseImage(int w, int h, String format) throws Exception {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setRGB(x, y, new Color(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256)).getRGB());
			}
		}
		var out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	static {
		try {
			Files.createDirectories(IMAGE_DIR);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
