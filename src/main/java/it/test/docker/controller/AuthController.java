package it.test.docker.controller;

import java.text.SimpleDateFormat;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@EnableOAuth2Sso
@Slf4j
public class AuthController {
	
	@Autowired
	private OAuth2AuthorizedClientService authorizedClientService;
	
	@GetMapping("/")
	@ResponseBody
	public ResponseEntity<Object> index(OAuth2AuthenticationToken authentication) {
		// authentication.getAuthorizedClientRegistrationId() returns the
		// registrationId of the Client that was authorized during the Login flow
		
		if (null == authentication) {
			log.info("Login - Not authenticated - Redirect to login");
			return getRedirectToLogin();
		}
		
		log.info("Login - User Principal (auth registration id " + authentication.getAuthorizedClientRegistrationId() + ") : " + authentication.getPrincipal());
		
		OAuth2AuthorizedClient authorizedClient =
			this.authorizedClientService.loadAuthorizedClient(
				authentication.getAuthorizedClientRegistrationId(),
				authentication.getName());

		if (null == authorizedClient) {
			log.info("Login - Not authorized client - Redirect to login");
			return getRedirectToLogin();
		}
		
		OAuth2User user = authentication.getPrincipal();
		
		StringBuilder response = new StringBuilder();
		response.append("User : <i>").append(user.getAttributes().get("name")).append("</i><br/>");
		response.append("Email : <i>").append(user.getAttributes().get("email")).append("</i><br/>");
		response.append("User Id : <i>").append(authentication.getName()).append("</i><br/>");
		response.append("<br/><br/><br/>");
		
		if (null != authorizedClient.getRefreshToken()) {
			log.info("Login - Refresh token - Value = [" + authorizedClient.getRefreshToken().getTokenValue() + "]");
			log.info("Login - Refresh token - Issued at = [" + formatInstant(authorizedClient.getRefreshToken().getIssuedAt()) + "]");
			log.info("Login - Refresh token - Expires at = [" + formatInstant(authorizedClient.getRefreshToken().getExpiresAt()) + "]");
		
			response.append("Refresh token (expires at ")
				.append(formatInstant(authorizedClient.getRefreshToken().getExpiresAt())).append("): <br/>")
				.append("<b>").append(authorizedClient.getRefreshToken().getTokenValue()).append("</b><br/><br/><br/>");
		}
		
		OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

		log.info("Login - Access token - Value = [" + accessToken.getTokenType().getValue() + " " + accessToken.getTokenValue() + "]");
		log.info("Login - Access token - Issued at = [" + formatInstant(accessToken.getIssuedAt()) + "]");
		log.info("Login - Access token - Expires at = [" + formatInstant(accessToken.getExpiresAt()) + "]");
		log.info("Login - Access token - Scopes = [" + accessToken.getScopes() + "]");
		
		response.append("Access token (expires at ").append(formatInstant(accessToken.getExpiresAt())).append(") : <br>")
			.append("<b>").append(accessToken.getTokenType().getValue()).append("<br/>").append(accessToken.getTokenValue()).append("</b><br/><br/><br/>");
		
		response.append("<br/><br/><br/>");
		response.append("Logout:<br/><a href='/logout'>logout</a>");
				
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.TEXT_HTML);
		responseHeaders.setContentLength(response.length());
		responseHeaders.set("CUSTOM-HEADER-RESPONSE", "some-value...");
		
		return ResponseEntity
				.status(HttpStatus.OK)
				.headers(responseHeaders)
				.body(response.toString());
	}
	
	
	private String formatInstant(Instant i) {
		return null == i ? "(never)" : new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(i.toEpochMilli());
	}
	
	
	
	private ResponseEntity<Object> getRedirectToLogin() {
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("Location", "/login");
		
		return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
		
	}
	
}
