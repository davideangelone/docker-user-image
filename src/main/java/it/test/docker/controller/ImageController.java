package it.test.docker.controller;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import io.minio.ErrorCode;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class ImageController {
	
	private static final String USER_URL = System.getenv("OKTA_USER_URL");
	private static final String MINIO_ENDPOINT = System.getenv("MINIO_ENDPOINT");
	private static final String MINIO_ACCESSKEYID = System.getenv("MINIO_USER");
	private static final String MINIO_SECRETACCESSKEY = System.getenv("MINIO_PASSWORD");
	private static final String MINIO_BUCKETNAME = System.getenv("MINIO_BUCKET");
	private static final String PREFIX_PHOTO = "photo-";
	
	
	public static class UserImage {
		
		@Getter @Setter
		private String user;
		
		@Getter @Setter
		private String image;
		
		public UserImage() {
		}
		
		public UserImage(String user, String image) {
			this.user = user;
			this.image = image;
		}
		
	}
	

	private MinioClient getMinioClient() throws Exception {
		
		MinioClient minioClient = new MinioClient(MINIO_ENDPOINT, MINIO_ACCESSKEYID, MINIO_SECRETACCESSKEY);
		minioClient.traceOn(System.out);
		
		// Check if the bucket already exists
	    boolean isExist = minioClient.bucketExists(MINIO_BUCKETNAME);
	    if (!isExist) {
	    	// Make a new bucket
	        minioClient.makeBucket(MINIO_BUCKETNAME);
	    }
	    
	    return minioClient;
	      
	}
	
	private boolean isObjectExisting(MinioClient minioClient, String user) throws Exception {
		
		boolean exists = true;
		
		try {
			minioClient.statObject(MINIO_BUCKETNAME, PREFIX_PHOTO + user);
		} catch (ErrorResponseException e) {
			ErrorCode code = e.errorResponse().errorCode();
			if (code == ErrorCode.NO_SUCH_KEY || code == ErrorCode.NO_SUCH_OBJECT) {
			    exists = false;
			} else {
			    throw e;
			}
		}
		
		return exists;
	}
	
	private byte[] getRawImage(String user) throws Exception {
		
		log.info("Get image for user [" + user + "]");
		
		byte[] image = null;
		
		MinioClient minioClient = getMinioClient();
		
		if (isObjectExisting(minioClient, user)) {
			
			// Get photo from the bucket with getObject
		    InputStream is = minioClient.getObject(MINIO_BUCKETNAME,  PREFIX_PHOTO + user);
		    if (null != is) {
		    	image = StreamUtils.copyToByteArray(is);
		    	log.info("Image for user [" + user + "] found (" + image.length + " bytes)");
		    } else {
		    	log.info("Image for user [" + user + "] not found");
		    }
		}
		
	    return image;
	}
	
	
	private HttpStatus putRawImage(String user, byte[] image) throws Exception {
		
		byte[] imageRaw = null;
		try {
			imageRaw = Base64.getDecoder().decode(image);
			// Immagine  passata in formato base 64
		} catch (IllegalArgumentException e) {
			//Immagine passata in formato binario
			imageRaw = image;
		}
        
		
		MinioClient minioClient = getMinioClient();
		
		boolean updated = isObjectExisting(minioClient, user);
		
		// Upload photo to the bucket with putObject
	    ByteArrayInputStream bais = new ByteArrayInputStream(imageRaw);
	    minioClient.putObject(MINIO_BUCKETNAME, PREFIX_PHOTO + user, bais, Long.valueOf(bais.available()), null, null, "application/octet-stream");
	    bais.close();
	      
		log.info(updated ? "Immagine aggiornata" : "Immagine registrata");
		return updated ? HttpStatus.OK : HttpStatus.CREATED;
	}
	

	@GetMapping(path = "/userinfo")
	@ResponseBody
	public ResponseEntity<byte[]> getUserInfo(
			@RequestHeader(name = "Authorization", required = false) String bearer) throws Exception {
		
		log.info("----");
		log.info("ImageController - get - Auth=[" + bearer + "]");
		
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, bearer);
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    
		HttpEntity<?> requestEntity = new HttpEntity<>(headers);

		ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<Map<String, Object>>() {};
		ResponseEntity<Map<String, Object>> userAttributes = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		
		RestTemplate restTemplate = new RestTemplate();
		
		try {
			userAttributes = restTemplate
				.exchange(USER_URL, HttpMethod.GET, requestEntity, typeRef);
		} catch (RestClientResponseException e) {
			log.info("ImageController - get - Errore richiesta [" + e.getRawStatusCode() + "] : " + e);
			
			HttpStatus status = e.getRawStatusCode() == HttpStatus.UNAUTHORIZED.value() ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
			return ResponseEntity
					.status(status)
					.build();
		}
		log.info("ImageController - get - userAttributes=" + userAttributes);
		
		String user = null;
	    
		if (HttpStatus.OK == userAttributes.getStatusCode()) {
			
			user = (String) userAttributes.getBody().get("sub");
			
			HttpStatus status = HttpStatus.OK;

			HttpHeaders responseHeaders = new HttpHeaders();
			responseHeaders.setContentType(MediaType.IMAGE_JPEG);
			
			byte[] img = getRawImage(user);
			if (null == img) {
				responseHeaders.setContentLength(0);
				status = HttpStatus.NOT_FOUND;
				log.info("ImageController - get - Image not found");
			}
			else {
				responseHeaders.setContentLength(img.length);
			}
			
			return ResponseEntity
					.status(status)
					.headers(responseHeaders)
					.body(img);
			
		}
		else if (HttpStatus.UNAUTHORIZED == userAttributes.getStatusCode()) {
			log.info("ImageController - get - User unauthorized");
			return ResponseEntity
					.status(HttpStatus.UNAUTHORIZED)
					.build();
		}
		else {
			
			log.info("ImageController - get - ERRORE! Esito " + userAttributes.getStatusCode());
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.build();
		}

	}
	
	
	@PostMapping(path = "/userinfo", consumes = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<?> postUserInfo(
			@RequestHeader(name = "Authorization", required = false) String bearer,
			@RequestBody(required =  false) byte[] body) throws Exception {
		
		log.info("----");
		log.info("ImageController - post - Auth=[" + bearer + "]");
		log.info("ImageController - post - Image [" + body + "]");
		
		if ( (null == body) || body.length == 0 ) {
			log.info("ImageController - post - Immagine non presente!");
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.build();
		}
		
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, bearer);
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    
		HttpEntity<?> requestEntity = new HttpEntity<>(headers);

		ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<Map<String, Object>>() {};
		ResponseEntity<Map<String, Object>> userAttributes = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		
		RestTemplate restTemplate = new RestTemplate();
		
		try {
			userAttributes = restTemplate
				.exchange(USER_URL, HttpMethod.GET, requestEntity, typeRef);
		} catch (RestClientResponseException e) {
			log.info("ImageController - post - Errore richiesta [" + e.getRawStatusCode() + "] : " + e);
			
			HttpStatus status = e.getRawStatusCode() == HttpStatus.UNAUTHORIZED.value() ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
			return ResponseEntity
					.status(status)
					.build();

		}
		log.info("ImageController - post - userAttributes=" + userAttributes);
		
		String user = null;
	    
		if (HttpStatus.OK == userAttributes.getStatusCode()) {
			user = (String) userAttributes.getBody().get("sub");
			log.info("ImageController - post - User = " + user);
			
			HttpStatus status = putRawImage(user, body);
			
			return ResponseEntity
					.status(status)
					.build();
		}
		else if (HttpStatus.UNAUTHORIZED == userAttributes.getStatusCode()) {
			log.info("ImageController - post - User unauthorized");
			return ResponseEntity
					.status(HttpStatus.UNAUTHORIZED)
					.build();
		}
		else {
			
			log.info("ImageController - post - ERRORE! Esito " + userAttributes.getStatusCode());
			return ResponseEntity
					.status(HttpStatus.BAD_REQUEST)
					.build();
		}
		
	}

}
