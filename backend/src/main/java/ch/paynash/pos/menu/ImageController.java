package ch.paynash.pos.menu;

import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Serves item images. URLs contain a content hash, so responses can be cached forever. */
@RestController
@Tag(name = "Menu")
public class ImageController {

	private final ImageStore images;

	public ImageController(ImageStore images) {
		this.images = images;
	}

	@GetMapping("/images/{ref}")
	@SecurityRequirements
	@Operation(summary = "Item image (WebP)")
	public ResponseEntity<Resource> image(@PathVariable String ref) {
		return images.find(ref)
			.<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("image/webp"))
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.body(new FileSystemResource(path)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
